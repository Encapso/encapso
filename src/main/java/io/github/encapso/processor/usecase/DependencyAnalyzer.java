package io.github.encapso.processor.usecase;

import io.github.encapso.processor.ProcessorUtils;
import io.github.encapso.processor.domain.DependencyGraph;
import io.github.encapso.processor.domain.DependencyGraph.DependencyKind;
import io.github.encapso.processor.domain.DependencyGraph.ExternalDependency;
import io.github.encapso.processor.domain.DependencyGraph.InstantiationStep;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Recursively analyses the constructor dependency tree of all target classes.
 * Produces a {@link DependencyGraph} in topological order.
 */
public class DependencyAnalyzer {

    private final Elements elements;
    private final AnnotationMatcher annotationMatcher;
    private final InstantiationPointSelector selector;

    public DependencyAnalyzer(Elements elements, InstantiationPointSelector selector) {
        this.elements = elements;
        this.selector = selector;
        this.annotationMatcher = new AnnotationMatcher();
    }

    public DependencyGraph analyze(TypeElement componentInterface, Map<TypeElement, String> targetToFactory, String componentPackage) {
        Set<TypeElement> baseTargets = targetToFactory.keySet();
        InstanceNamingStrategy namingStrategy = new InstanceNamingStrategy();

        // 1. Discover all internal classes reachable from the TCs
        Map<TypeElement, ExecutableElement> internalToConstructor = discoverReachableInternals(componentInterface, targetToFactory, componentPackage, baseTargets);

        // 2. Classify parameters for each internal class
        Map<TypeElement, List<ParamInfo>> classToParams = internalToConstructor.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> classifyParameters(e.getValue(), componentPackage, baseTargets),
                        (v1, v2) -> v1, LinkedHashMap::new));

        // 3. Collect and name unique external dependencies
        List<ExternalDependency> externalDependencies = collectExternalDependencies(classToParams, namingStrategy);

        // 4. Allocate collision-free instance names for internal classes
        Map<TypeElement, String> internalNames = internalToConstructor.keySet().stream()
                .collect(Collectors.toMap(
                        type -> type,
                        type -> namingStrategy.allocate(type.getSimpleName().toString()),
                        (v1, v2) -> v1, LinkedHashMap::new));

        // 5. Order internals topologically and build instantiation steps
        List<InstantiationStep> steps = buildInstantiationSteps(componentInterface, internalToConstructor.keySet(), classToParams, internalNames, externalDependencies, targetToFactory);

        // 6. Map original target classes to their canonical instance names and types
        Map<TypeElement, String> targetClassToInstanceName = new LinkedHashMap<>();
        Map<TypeElement, com.squareup.javapoet.TypeName> targetClassToInstanceType = new LinkedHashMap<>();

        for (InstantiationStep step : steps) {
            if (baseTargets.contains(step.type())) {
                targetClassToInstanceName.put(step.type(), step.instanceName());
                targetClassToInstanceType.put(step.type(), com.squareup.javapoet.TypeName.get(step.targetType()));
            }
        }

        return new DependencyGraph(externalDependencies, steps, targetClassToInstanceName, targetClassToInstanceType);
    }

    private Map<TypeElement, ExecutableElement> discoverReachableInternals(TypeElement componentInterface, Map<TypeElement, String> targetToFactory, String componentPackage, Set<TypeElement> baseTargets) {
        Map<TypeElement, ExecutableElement> discovered = new LinkedHashMap<>();
        for (TypeElement tc : baseTargets) {
            discoverRecursively(componentInterface, tc, targetToFactory.get(tc), componentPackage, baseTargets, discovered);
        }
        return discovered;
    }

    private void discoverRecursively(TypeElement componentInterface, TypeElement type, String factoryMethod, String componentPackage, Set<TypeElement> baseTargets, Map<TypeElement, ExecutableElement> discovered) {
        if (discovered.containsKey(type) || !isInternal(type, componentPackage, baseTargets)) return;

        ExecutableElement instantiationPoint = selector.select(componentInterface, type, factoryMethod);
        discovered.put(type, instantiationPoint);

        if (instantiationPoint != null) {
            for (VariableElement param : instantiationPoint.getParameters()) {
                TypeElement paramType = ProcessorUtils.toTypeElement(param.asType());
                if (paramType != null) {
                    discoverRecursively(componentInterface, paramType, null, componentPackage, baseTargets, discovered);
                }
            }
        }
    }

    private List<ExternalDependency> collectExternalDependencies(Map<TypeElement, List<ParamInfo>> classToParams, InstanceNamingStrategy namingStrategy) {
        Map<String, ExternalDependency> keyToExternal = new LinkedHashMap<>();
        for (List<ParamInfo> params : classToParams.values()) {
            for (ParamInfo p : params) {
                if (!p.internal()) {
                    String key = p.typeElement().getQualifiedName().toString() + ":" + p.paramName();
                    keyToExternal.computeIfAbsent(key, k -> {
                        String name = namingStrategy.allocate(p.paramName());
                        return new ExternalDependency(p.typeMirror(), name, p.paramName(), p.required(), DependencyKind.EXTERNAL);
                    });
                }
            }
        }
        return List.copyOf(keyToExternal.values());
    }

    private List<InstantiationStep> buildInstantiationSteps(TypeElement componentInterface,
                                                              Set<TypeElement> internalClasses,
                                                              Map<TypeElement, List<ParamInfo>> classToParams,
                                                              Map<TypeElement, String> internalNames,
                                                              List<ExternalDependency> externalDeps,
                                                              Map<TypeElement, String> targetToFactory) {
        List<TypeElement> sorted = topologicalSort(internalClasses, classToParams);
        List<InstantiationStep> steps = new ArrayList<>();
        
        for (TypeElement type : sorted) {
            List<ParamInfo> params = classToParams.get(type);
            List<String> argNames = params.stream()
                    .map(p -> {
                        if (p.internal()) {
                            return internalNames.get(p.typeElement());
                        } else {
                            return externalDeps.stream()
                                    .filter(ed -> ed.originalParamName().equals(p.paramName()))
                                    .filter(ed -> ed.type().toString().equals(p.typeMirror().toString()))
                                    .findFirst()
                                    .map(ExternalDependency::paramName)
                                    .orElse(null);
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
            steps.add(new InstantiationStep(type, type.asType(), internalNames.get(type), argNames, targetToFactory.get(type)));
        }
        return steps;
    }

    private List<ParamInfo> classifyParameters(ExecutableElement constructor, String componentPackage, Set<TypeElement> baseTargets) {
        if (constructor == null) return List.of();
        return constructor.getParameters().stream()
                .map(p -> {
                    TypeMirror mirror = p.asType();
                    TypeElement element = ProcessorUtils.toTypeElement(mirror);
                    if (element == null) return null;
                    
                    boolean internal = isInternal(element, componentPackage, baseTargets);
                    boolean required = annotationMatcher.isNonNull(p);
                    String name = p.getSimpleName().toString();
                    return new ParamInfo(element, mirror, name, internal, required);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private List<TypeElement> topologicalSort(Set<TypeElement> classes, Map<TypeElement, List<ParamInfo>> classToParams) {
        List<TypeElement> result = new ArrayList<>();
        Map<TypeElement, VisitState> states = new HashMap<>();

        for (TypeElement type : classes) {
            topoVisit(type, classes, classToParams, states, result, new ArrayList<>());
        }
        return result;
    }

    private void topoVisit(TypeElement type, Set<TypeElement> all, Map<TypeElement, List<ParamInfo>> classToParams, Map<TypeElement, VisitState> states, List<TypeElement> result, List<TypeElement> currentPath) {
        VisitState state = states.getOrDefault(type, VisitState.UNVISITED);

        if (state == VisitState.VISITING) {
            int startIndex = currentPath.indexOf(type);
            List<TypeElement> cycle = new ArrayList<>(currentPath.subList(startIndex, currentPath.size()));
            cycle.add(type);
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

    private boolean isInternal(TypeElement type, String componentPackage, Set<TypeElement> baseTargets) {
        if (type.getKind().isInterface()) return false;
        if (type.getAnnotation(io.github.encapso.Component.class) != null) return false;
        if (baseTargets.contains(type)) return true;
        
        if (componentPackage == null) return false;
        String pkg = elements.getPackageOf(type).getQualifiedName().toString();
        boolean inPackage = pkg.equals(componentPackage) || pkg.startsWith(componentPackage + ".");
        return inPackage && type.getTypeParameters().isEmpty();
    }

    private record ParamInfo(TypeElement typeElement, TypeMirror typeMirror, String paramName, boolean internal, boolean required) {}
}
