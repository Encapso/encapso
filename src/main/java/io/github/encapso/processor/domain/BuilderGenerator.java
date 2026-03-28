package io.github.encapso.processor.domain;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/** Outbound port for generating the public ComponentBuilder class. */
public interface BuilderGenerator {
    void generateBuilder(TypeElement interfaceElement,
                         DependencyGraph graph,
                         Filer filer,
                         Elements elements,
                         Types types,
                         Messager messager);
}
