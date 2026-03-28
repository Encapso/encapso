package io.github.encapso.processor.domain;

import io.github.encapso.processor.infrastructure.GeneratorContext;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.Map;

/** Outbound port for generating the component facade implementation. */
public interface FacadeGenerator {
    void generateFacade(TypeElement interfaceElement,
                        Map<ExecutableElement, TypeElement> delegateMapping,
                        GeneratorContext context);
}
