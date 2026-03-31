package io.github.encapso.processor.domain;

import com.squareup.javapoet.TypeName;
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
        Map<TypeElement, String> tcInstanceNames,
        Map<TypeElement, TypeName> tcInstanceTypes
) {
    /** The source of a dependency (where it comes from). */
    public enum DependencyKind {
        EXTERNAL, // Provided via builder
        INTERNAL   // Managed by the processor
    }

    /** A dependency that must be supplied externally via the builder. */
    public record ExternalDependency(TypeMirror type, String paramName, String originalParamName, boolean required, DependencyKind kind) {}

    /** One instantiation step in topological order inside build(). */
    public record InstantiationStep(
            TypeElement type,
            TypeMirror targetType,
            String instanceName,
            List<String> constructorArgs,
            String factoryMethod
    ) {}
}
