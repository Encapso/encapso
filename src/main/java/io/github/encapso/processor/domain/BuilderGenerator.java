package io.github.encapso.processor.domain;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

/** Outbound port for generating the public ComponentBuilder class. */
public interface BuilderGenerator {
    void generateBuilder(TypeElement interfaceElement,
                         DependencyGraph graph,
                         ProcessingEnvironment processingEnv);
}
