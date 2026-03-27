package io.github.encapso.processor.domain;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.Map;

/**
 * Outbound port for creating the underlying physical `.java` component output streams.
 */
public interface FacadeGenerator {
    
    void generateFacade(TypeElement interfaceElement, Map<ExecutableElement, TypeElement> delegateMapping, ProcessingEnvironment processingEnv);
}
