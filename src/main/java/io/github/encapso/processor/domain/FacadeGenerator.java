package io.github.encapso.processor.domain;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Map;

/** Outbound port for generating the component facade implementation. */
public interface FacadeGenerator {
    void generateFacade(TypeElement interfaceElement,
                        Map<ExecutableElement, TypeElement> delegateMapping,
                        DependencyGraph graph,
                        Filer filer,
                        Elements elements,
                        Types types,
                        Messager messager);
}
