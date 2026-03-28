package io.github.encapso.processor.domain;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Map;

/**
 * The result of recursive dependency analysis for a component.
 * Drives both the FacadeImpl and ComponentBuilder code generation.
 */
public record DependencyGraph(
        List<ExternalDependency> externalDependencies,
        List<InstantiationStep> instantiationSteps,
        Map<TypeElement, String> tcInstanceNames
) {
    /** A dependency that must be supplied externally via the builder. */
    public record ExternalDependency(TypeMirror type, String paramName) {}

    /** One instantiation step in topological order inside build(). */
    public record InstantiationStep(
            TypeElement type,
            TypeMirror targetType,
            String instanceName,
            List<String> constructorArgs
    ) {}
}
