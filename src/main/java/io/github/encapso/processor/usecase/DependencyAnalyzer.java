package io.github.encapso.processor.usecase;

import io.github.encapso.processor.domain.DependencyGraph;
import io.github.encapso.processor.domain.DependencyGraph.ExternalDependency;
import io.github.encapso.processor.domain.DependencyGraph.InstantiationStep;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.util.*;

/**
 * Recursively analyses the constructor dependency tree of all target classes.
 * Produces a {@link DependencyGraph} describing:
 * <ul>
 *   <li>All unique <em>external</em> dependencies (flattened, deduplicated by type)</li>
 *   <li>All internal instantiation steps in topological order</li>
 *   <li>The canonical instance name for each target class (for consistent naming)</li>
 * </ul>
 *
 * <p>Constructor selection rules (consistent with Spring DI):
 * <ol>
 *   <li>Single constructor → use it</li>
 *   <li>Multiple constructors → use the one with the most parameters</li>
 *   <li>Zero-arg / no-explicit constructor → instantiate directly, no external deps needed</li>
 * </ol>
 *
 * <p>A type is <em>internal</em> if it lives in the component package or a subpackage.
 * Everything else is <em>external</em> and must be supplied through the builder.
 */
public class DependencyAnalyzer {

    private final ProcessingEnvironment env;

    public DependencyAnalyzer(ProcessingEnvironment env) {
        this.env = env;
    }

    public DependencyGraph analyze(Collection<TypeElement> targetClasses, String componentPackage) {

        // Step 1: Recursively discover all internal classes reachable from the TCs
        Map<TypeElement, ExecutableElement> allInternals = new LinkedHashMap<>();
        for (TypeElement tc : targetClasses) {
            discoverInternals(tc, componentPackage, allInternals);
        }

        // Step 2: Classify each constructor parameter as internal or external
        Map<TypeElement, List<ParamInfo>> classParams = new LinkedHashMap<>();
        for (Map.Entry<TypeElement, ExecutableElement> entry : allInternals.entrySet()) {
            classParams.put(entry.getKey(), classifyParams(entry.getValue(), componentPackage));
        }

        // Step 3: Collect unique external deps (deduplicated by TypeElement identity)
        LinkedHashMap<TypeElement, ExternalDependency> externalMap = new LinkedHashMap<>();
        for (List<ParamInfo> params : classParams.values()) {
            for (ParamInfo p : params) {
                if (!p.internal()) {
                    externalMap.computeIfAbsent(p.type(),
                            t -> new ExternalDependency(t, uniqueName(t.getSimpleName().toString(), externalMap)));
                }
            }
        }

        // Step 4: Build a collision-free name map for ALL types (external + internal)
        Map<TypeElement, String> nameMap = new LinkedHashMap<>();
        Set<String> usedNames = new LinkedHashSet<>();
        for (ExternalDependency dep : externalMap.values()) {
            nameMap.put(dep.type(), dep.paramName());
            usedNames.add(dep.paramName());
        }
        for (TypeElement internal : allInternals.keySet()) {
            String name = allocateName(internal.getSimpleName().toString(), usedNames);
            nameMap.put(internal, name);
            usedNames.add(name);
        }

        // Step 5: Topological sort of internal classes (dependencies before dependents)
        List<TypeElement> sorted = topologicalSort(allInternals.keySet(), classParams);

        // Step 6: Build InstantiationSteps
        List<InstantiationStep> steps = new ArrayList<>();
        for (TypeElement type : sorted) {
            List<String> args = classParams.getOrDefault(type, List.of()).stream()
                    .map(p -> nameMap.get(p.type()))
                    .filter(Objects::nonNull)
                    .toList();
            steps.add(new InstantiationStep(type, nameMap.get(type), args));
        }

        // Step 7: TC instance names (preserves order from targetClasses — matches FacadeImpl ctor order)
        Map<TypeElement, String> tcInstanceNames = new LinkedHashMap<>();
        for (TypeElement tc : targetClasses) {
            tcInstanceNames.put(tc, nameMap.getOrDefault(tc, camelCase(tc.getSimpleName().toString())));
        }

        return new DependencyGraph(List.copyOf(externalMap.values()), steps, tcInstanceNames);
    }

    // -------------------------------------------------------------------------

    private void discoverInternals(TypeElement type, String componentPackage,
                                   Map<TypeElement, ExecutableElement> discovered) {
        if (discovered.containsKey(type)) return;
        if (!isInternal(type, componentPackage)) return;

        ExecutableElement ctor = selectConstructor(type);
        discovered.put(type, ctor);

        if (ctor != null) {
            for (VariableElement param : ctor.getParameters()) {
                TypeElement paramType = toTypeElement(param.asType());
                if (paramType != null && isInternal(paramType, componentPackage)) {
                    discoverInternals(paramType, componentPackage, discovered);
                }
            }
        }
    }

    private List<ParamInfo> classifyParams(ExecutableElement ctor, String componentPackage) {
        if (ctor == null) return List.of();
        List<ParamInfo> result = new ArrayList<>();
        for (VariableElement param : ctor.getParameters()) {
            TypeElement type = toTypeElement(param.asType());
            if (type == null) continue; // primitive — skip
            result.add(new ParamInfo(type, isInternal(type, componentPackage)));
        }
        return result;
    }

    private ExecutableElement selectConstructor(TypeElement type) {
        List<ExecutableElement> ctors = ElementFilter.constructorsIn(type.getEnclosedElements());
        if (ctors.isEmpty()) return null;
        return ctors.stream()
                .max(Comparator.comparingInt(c -> c.getParameters().size()))
                .orElse(null);
    }

    private boolean isInternal(TypeElement type, String componentPackage) {
        String pkg = env.getElementUtils().getPackageOf(type).getQualifiedName().toString();
        return pkg.equals(componentPackage) || pkg.startsWith(componentPackage + ".");
    }

    private TypeElement toTypeElement(TypeMirror mirror) {
        if (mirror instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) return te;
        return null;
    }

    private List<TypeElement> topologicalSort(Set<TypeElement> classes,
                                              Map<TypeElement, List<ParamInfo>> classParams) {
        List<TypeElement> result = new ArrayList<>();
        Set<TypeElement> visited = new HashSet<>();
        for (TypeElement type : classes) {
            topoVisit(type, classes, classParams, visited, result);
        }
        return result;
    }

    private void topoVisit(TypeElement type, Set<TypeElement> all,
                           Map<TypeElement, List<ParamInfo>> classParams,
                           Set<TypeElement> visited, List<TypeElement> result) {
        if (visited.contains(type)) return;
        visited.add(type);
        for (ParamInfo p : classParams.getOrDefault(type, List.of())) {
            if (p.internal() && all.contains(p.type())) {
                topoVisit(p.type(), all, classParams, visited, result);
            }
        }
        result.add(type);
    }

    private String allocateName(String simpleName, Set<String> usedNames) {
        String base = camelCase(simpleName);
        String name = base;
        int counter = 1;
        while (usedNames.contains(name)) {
            name = base + (++counter);
        }
        return name;
    }

    private String uniqueName(String simpleName, Map<TypeElement, ExternalDependency> existing) {
        Set<String> used = new HashSet<>();
        for (ExternalDependency d : existing.values()) used.add(d.paramName());
        return allocateName(simpleName, used);
    }

    private String camelCase(String name) {
        if (name.isEmpty()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private record ParamInfo(TypeElement type, boolean internal) {}
}
