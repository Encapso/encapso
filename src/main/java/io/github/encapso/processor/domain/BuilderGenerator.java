package io.github.encapso.processor.domain;

import javax.lang.model.element.TypeElement;

import io.github.encapso.processor.infrastructure.GeneratorContext;

/** Outbound port for generating the public ComponentBuilder class. */
public interface BuilderGenerator {
    void generateBuilder(TypeElement interfaceElement, GeneratorContext context);
}
