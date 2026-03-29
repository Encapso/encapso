package io.github.encapso.processor.usecase;

import io.github.encapso.processor.domain.DependencyGraph;
import io.github.encapso.processor.domain.DependencyGraph.DependencyKind;
import io.github.encapso.processor.domain.DependencyGraph.ExternalDependency;
import io.github.encapso.processor.domain.DependencyGraph.InstantiationStep;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    private final Elements elements;
    private final AnnotationMatcher annotationMatcher;

    public DependencyAnalyzer(Elements elements) {
        this.elements = elements;
        this.annotationMatcher = new AnnotationMatcher();
    }

    public DependencyGraph analyze(TypeElement componentInterface, Map<TypeElement, String> targetToFactory, String componentPackage) {
        Set<TypeElement> baseTargets = targetToFactory.keySet();
        // Step 1: Discover all internal classes reachable from the TCs
        Map<TypeElement, ExecutableElement> internalToConstructor = discoverReachableInternals(targetToFactory, componentPackage, baseTargets);

        // Step 2: Classify parameters for each internal class (context-aware)
        Map<TypeElement, List<ParamInfo>> classToParams = new LinkedHashMap<>();
        for (Map.Entry<TypeElement, ExecutableElement> entry : internalToConstructor.entrySet()) {
            classToParams.put(entry.getKey(), classifyParameters(entry.getValue(), componentPackage, baseTargets));
        }

        // Step 3: Collect and name unique external dependencies (preserving signatures)
        List<ExternalDependency> externalDependencies = collectExternalDependencies(classToParams);

        // Step 4: Allocate collision-free instance names for internal classes
        // External names are already allocated in step 3
        Map<TypeElement, String> internalNames = allocateInternalInstanceNames(internalToConstructor.keySet(), externalDependencies);

        // 5. Order internals topologically and build instantiation steps
        List<InstantiationStep> steps = buildInstantiationSteps(internalToConstructor.keySet(), classToParams, internalNames, externalDependencies, targetToFactory);

        // 6. Map original target classes to their canonical instance names and types
        Map<TypeElement, String> targetClassToInstanceName = new LinkedHashMap<>();
        Map<TypeElement, com.squareup.javapoet.TypeName> targetClassToInstanceType = new LinkedHashMap<>();

        // We collect the types from the instantiation steps to get the resolved generics
        for (InstantiationStep step : steps) {
            if (baseTargets.contains(step.type())) {
                targetClassToInstanceName.put(step.type(), step.instanceName());
                targetClassToInstanceType.put(step.type(), com.squareup.javapoet.TypeName.get(step.targetType()));
            }
        }

        return new io.github.encapso.processor.domain.DependencyGraph(externalDependencies, steps, targetClassToInstanceName, targetClassToInstanceType);
    }

    // --- Core Steps ---

    private Map<TypeElement, ExecutableElement> discoverReachableInternals(Map<TypeElement, String> targetToFactory, String componentPackage, Set<TypeElement> baseTargets) {
        Map<TypeElement, ExecutableElement> discovered = new LinkedHashMap<>();
        for (TypeElement tc : baseTargets) {
            String factoryMethod = targetToFactory.get(tc);
            discoverRecursively(tc, factoryMethod, componentPackage, baseTargets, discovered);
        }
        return discovered;
    }

    private void discoverRecursively(TypeElement type, String factoryMethod, String componentPackage, Set<TypeElement> baseTargets, Map<TypeElement, ExecutableElement> discovered) {
        if (discovered.containsKey(type) || !isInternal(type, componentPackage, baseTargets)) return;

        ExecutableElement instantiationPoint = selectInstantiationPoint(type, factoryMethod);
        discovered.put(type, instantiationPoint);

        if (instantiationPoint != null) {
            for (VariableElement param : instantiationPoint.getParameters()) {
                TypeElement paramType = toTypeElement(param.asType());
                if (paramType != null) {
                    // Internal dependencies (transitive) currently always use constructors (factoryMethod = null)
                    discoverRecursively(paramType, null, componentPackage, baseTargets, discovered);
                }
            }
        }
    }

    private List<ExternalDependency> collectExternalDependencies(Map<TypeElement, List<ParamInfo>> classToParams) {
        // Use a composite key of (TypeElement, Name) to allow multiple instances of the same type
        Map<String, ExternalDependency> keyToExternal = new LinkedHashMap<>();
        for (List<ParamInfo> params : classToParams.values()) {
            for (ParamInfo p : params) {
                if (!p.internal()) {
                    String key = p.typeElement().getQualifiedName().toString() + ":" + p.paramName();
                    keyToExternal.computeIfAbsent(key, k -> {
                        // The builder setter name will still be unique thanks to allocateUniqueName
                        String builderSetterName = allocateUniqueName(p.paramName(), getNames(keyToExternal.values()));
                        return new ExternalDependency(p.typeMirror(), builderSetterName, p.required(), DependencyKind.EXTERNAL);
                    });
                }
            }
        }
        return List.copyOf(keyToExternal.values());
    }

    private Map<TypeElement, String> allocateInternalInstanceNames(Set<TypeElement> internalClasses, List<ExternalDependency> externalDeps) {
        Map<TypeElement, String> nameMap = new LinkedHashMap<>();
        Set<String> usedNames = new HashSet<>();

        // Record external names to prevent collisions
        for (ExternalDependency dep : externalDeps) {
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
                                                             Map<TypeElement, String> internalNames,
                                                             List<ExternalDependency> externalDeps,
                                                             Map<TypeElement, String> targetToFactory) {
        List<TypeElement> sorted = topologicalSort(internalClasses, classToParams);
        List<InstantiationStep> steps = new ArrayList<>();
        
        for (TypeElement type : sorted) {
            String factoryMethod = targetToFactory.get(type);
            List<String> argNames = classToParams.getOrDefault(type, List.of()).stream()
                    .map(p -> {
                        if (p.internal()) {
                            return internalNames.get(p.typeElement());
                        } else {
                            // Find corresponding external dependency by type and name match
                            return externalDeps.stream()
                                    .filter(ed -> ed.paramName().equals(p.paramName()) || ed.paramName().startsWith(p.paramName()))
                                    .filter(ed -> ed.type().toString().equals(p.typeMirror().toString()))
                                    .findFirst()
                                    .map(ExternalDependency::paramName)
                                    .orElse(null);
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
            steps.add(new InstantiationStep(type, type.asType(), internalNames.get(type), argNames, factoryMethod));
        }
        return steps;
    }

    // --- Helpers ---

    private List<ParamInfo> classifyParameters(ExecutableElement constructor, String componentPackage, Set<TypeElement> baseTargets) {
        if (constructor == null) return List.of();
        return constructor.getParameters().stream()
                .map(p -> {
                    TypeMirror mirror = p.asType();
                    TypeElement element = toTypeElement(mirror);
                    if (element == null) return null;
                    
                    boolean internal = isInternal(element, componentPackage, baseTargets);
                    boolean required = isRequired(p);
                    
                    String name = p.getSimpleName().toString();
                    return new ParamInfo(element, mirror, name, internal, required);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean isRequired(VariableElement param) {
        return annotationMatcher.isNonNull(param);
    }

    private ExecutableElement selectInstantiationPoint(TypeElement type, String factoryMethodName) {
        if (factoryMethodName == null || factoryMethodName.isEmpty()) {
            List<ExecutableElement> constructors = ElementFilter.constructorsIn(type.getEnclosedElements());
            return constructors.stream()
                    .max(Comparator.comparingInt(c -> c.getParameters().size()))
                    .orElse(null);
        }

        // Search for static factory method
        return ElementFilter.methodsIn(type.getEnclosedElements()).stream()
                .filter(m -> m.getSimpleName().toString().equals(factoryMethodName))
                .filter(m -> m.getModifiers().contains(javax.lang.model.element.Modifier.STATIC))
                .max(Comparator.comparingInt(m -> m.getParameters().size()))
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Static factory method '%s' not found in class %s",
                                factoryMethodName, type.getQualifiedName())));
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
            if (p.internal() && all.contains(p.typeElement())) {
                topoVisit(p.typeElement(), all, classToParams, states, result, currentPath);
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

    private boolean isInternal(TypeElement type, String componentPackage, Set<TypeElement> baseTargets) {
        // Interfaces are never internal (must be provided externally or handled by a factory)
        if (type.getKind().isInterface()) return false;

        // Anything annotated with @Component is a boundary, not an internal implementation.
        if (type.getAnnotation(io.github.encapso.Component.class) != null) return false;

        if (baseTargets.contains(type)) return true;
        
        String pkg = elements.getPackageOf(type).getQualifiedName().toString();
        boolean inPackage = pkg.equals(componentPackage) || pkg.startsWith(componentPackage + ".");
        
        // Non-primary targets are only internal if they are in the same package AND are not generic.
        // We can't auto-manage generic classes because we don't know the type parameters to use during 'new'.
        return inPackage && type.getTypeParameters().isEmpty();
    }

    private TypeElement toTypeElement(TypeMirror mirror) {
        if (mirror instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) return te;
        return null;
    }

    private String camelCase(String name) {
        if (name.isEmpty()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private record ParamInfo(TypeElement typeElement, TypeMirror typeMirror, String paramName, boolean internal, boolean required) {}
}
