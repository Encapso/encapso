package io.github.encapso.processor.infrastructure;

import io.github.encapso.processor.domain.DependencyGraph;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Encapsulates the processing environment and model state for code generation.
 * Reduces parameter bloat across the infrastructure layer.
 */
public record GeneratorContext(
    Filer filer,
    Elements elements,
    Types types,
    Messager messager,
    DependencyGraph graph
) {
    public String getPackageName(javax.lang.model.element.TypeElement element) {
        return elements.getPackageOf(element).getQualifiedName().toString();
    }
}
