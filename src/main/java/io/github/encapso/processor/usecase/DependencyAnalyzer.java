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
 */
public class DependencyAnalyzer {

    private final ProcessingEnvironment env;

    public DependencyAnalyzer(ProcessingEnvironment env) {
        this.env = env;
    }

    public DependencyGraph analyze(Collection<TypeElement> targetClasses, String componentPackage) {
        // Step 1: Discover all internal classes reachable from the TCs
        Map<TypeElement, ExecutableElement> internalToConstructor = discoverReachableInternals(targetClasses, componentPackage);

        // Step 2: Classify parameters for each internal class
        Map<TypeElement, List<ParamInfo>> classToParams = new LinkedHashMap<>();
        for (Map.Entry<TypeElement, ExecutableElement> entry : internalToConstructor.entrySet()) {
            classToParams.put(entry.getKey(), classifyParameters(entry.getValue(), componentPackage));
        }

        // Step 3: Collect and name unique external dependencies
        List<ExternalDependency> externalDependencies = collectExternalDependencies(classToParams);

        // Step 4: Allocate collision-free instance names for all types
        Map<TypeElement, String> typeToInstanceName = allocateInstanceNames(internalToConstructor.keySet(), externalDependencies);

        // Step 5: Order internals topologically and build instantiation steps
        List<InstantiationStep> steps = buildInstantiationSteps(internalToConstructor.keySet(), classToParams, typeToInstanceName);

        // Step 6: Map original target classes to their canonical instance names
        Map<TypeElement, String> targetClassToInstanceName = mapTargetClassInstanceNames(targetClasses, typeToInstanceName);

        return new DependencyGraph(externalDependencies, steps, targetClassToInstanceName);
    }

    // --- Core Steps ---

    private Map<TypeElement, ExecutableElement> discoverReachableInternals(Collection<TypeElement> targetClasses, String componentPackage) {
        Map<TypeElement, ExecutableElement> discovered = new LinkedHashMap<>();
        for (TypeElement tc : targetClasses) {
            discoverRecursively(tc, componentPackage, discovered);
        }
        return discovered;
    }

    private void discoverRecursively(TypeElement type, String componentPackage, Map<TypeElement, ExecutableElement> discovered) {
        if (discovered.containsKey(type) || !isInternal(type, componentPackage)) return;

        ExecutableElement constructor = selectConstructor(type);
        discovered.put(type, constructor);

        if (constructor != null) {
            for (VariableElement param : constructor.getParameters()) {
                TypeElement paramType = toTypeElement(param.asType());
                if (paramType != null) {
                    discoverRecursively(paramType, componentPackage, discovered);
                }
            }
        }
    }

    private List<ExternalDependency> collectExternalDependencies(Map<TypeElement, List<ParamInfo>> classToParams) {
        Map<TypeElement, ExternalDependency> typeToExternal = new LinkedHashMap<>();
        for (List<ParamInfo> params : classToParams.values()) {
            for (ParamInfo p : params) {
                if (!p.internal()) {
                    typeToExternal.computeIfAbsent(p.type(), t -> {
                        String name = allocateUniqueName(t.getSimpleName().toString(), getNames(typeToExternal.values()));
                        return new ExternalDependency(t, name);
                    });
                }
            }
        }
        return List.copyOf(typeToExternal.values());
    }

    private Map<TypeElement, String> allocateInstanceNames(Set<TypeElement> internalClasses, List<ExternalDependency> externalDeps) {
        Map<TypeElement, String> nameMap = new LinkedHashMap<>();
        Set<String> usedNames = new HashSet<>();

        // Register external names first
        for (ExternalDependency dep : externalDeps) {
            nameMap.put(dep.type(), dep.paramName());
            usedNames.add(dep.paramName());
        }

        // Allocate names for internals
        for (TypeElement internal : internalClasses) {
            String name = allocateUniqueName(internal.getSimpleName().toString(), usedNames);
            nameMap.put(internal, name);
            usedNames.add(name);
        }
        return nameMap;
    }

    private List<InstantiationStep> buildInstantiationSteps(Set<TypeElement> internalClasses,
                                                             Map<TypeElement, List<ParamInfo>> classToParams,
                                                             Map<TypeElement, String> typeToInstanceName) {
        List<TypeElement> sorted = topologicalSort(internalClasses, classToParams);
        List<InstantiationStep> steps = new ArrayList<>();
        for (TypeElement type : sorted) {
            List<String> argNames = classToParams.getOrDefault(type, List.of()).stream()
                    .map(p -> typeToInstanceName.get(p.type()))
                    .filter(Objects::nonNull)
                    .toList();
            steps.add(new InstantiationStep(type, typeToInstanceName.get(type), argNames));
        }
        return steps;
    }

    private Map<TypeElement, String> mapTargetClassInstanceNames(Collection<TypeElement> targetClasses, Map<TypeElement, String> typeToInstanceName) {
        Map<TypeElement, String> mapping = new LinkedHashMap<>();
        for (TypeElement tc : targetClasses) {
            mapping.put(tc, typeToInstanceName.get(tc));
        }
        return mapping;
    }

    // --- Helpers ---

    private List<ParamInfo> classifyParameters(ExecutableElement constructor, String componentPackage) {
        if (constructor == null) return List.of();
        return constructor.getParameters().stream()
                .map(p -> toTypeElement(p.asType()))
                .filter(Objects::nonNull)
                .map(t -> new ParamInfo(t, isInternal(t, componentPackage)))
                .toList();
    }

    private ExecutableElement selectConstructor(TypeElement type) {
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(type.getEnclosedElements());
        return constructors.stream()
                .max(Comparator.comparingInt(c -> c.getParameters().size()))
                .orElse(null);
    }

    private List<TypeElement> topologicalSort(Set<TypeElement> classes, Map<TypeElement, List<ParamInfo>> classToParams) {
        List<TypeElement> result = new ArrayList<>();
        Map<TypeElement, VisitState> states = new HashMap<>();

        for (TypeElement type : classes) {
            topoVisit(type, classes, classToParams, states, result, new ArrayList<>());
        }
        return result;
    }

    private void topoVisit(TypeElement type,
                           Set<TypeElement> all,
                           Map<TypeElement, List<ParamInfo>> classToParams,
                           Map<TypeElement, VisitState> states,
                           List<TypeElement> result,
                           List<TypeElement> currentPath) {
        VisitState state = states.getOrDefault(type, VisitState.UNVISITED);

        if (state == VisitState.VISITING) {
            // Cycle detected! Reconstruct the actual cycle path from currentPath
            int startIndex = currentPath.indexOf(type);
            List<TypeElement> cycle = new ArrayList<>(currentPath.subList(startIndex, currentPath.size()));
            cycle.add(type); // close the loop for reporting
            throw new io.github.encapso.processor.domain.CircularDependencyException(cycle);
        }

        if (state == VisitState.VISITED) return;

        states.put(type, VisitState.VISITING);
        currentPath.add(type);

        for (ParamInfo p : classToParams.getOrDefault(type, List.of())) {
            if (p.internal() && all.contains(p.type())) {
                topoVisit(p.type(), all, classToParams, states, result, currentPath);
            }
        }

        states.put(type, VisitState.VISITED);
        currentPath.remove(currentPath.size() - 1);
        result.add(type);
    }

    private enum VisitState { UNVISITED, VISITING, VISITED }

    private String allocateUniqueName(String simpleName, Set<String> usedNames) {
        String base = camelCase(simpleName);
        String current = base;
        int counter = 1;
        while (usedNames.contains(current)) {
            current = base + (++counter);
        }
        return current;
    }

    private Set<String> getNames(Collection<ExternalDependency> deps) {
        Set<String> names = new HashSet<>();
        for (ExternalDependency d : deps) names.add(d.paramName());
        return names;
    }

    private boolean isInternal(TypeElement type, String componentPackage) {
        String pkg = env.getElementUtils().getPackageOf(type).getQualifiedName().toString();
        return pkg.equals(componentPackage) || pkg.startsWith(componentPackage + ".");
    }

    private TypeElement toTypeElement(TypeMirror mirror) {
        if (mirror instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) return te;
        return null;
    }

    private String camelCase(String name) {
        if (name.isEmpty()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private record ParamInfo(TypeElement type, boolean internal) {}
}
