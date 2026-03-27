package io.github.encapso.processor.domain;

import javax.lang.model.element.Element;

/**
 * Agnostic port to decouple Java compilation console error reporting.
 */
public interface Reporter {
    
    /**
     * Prints a fatal compiler-crashing error binding precisely to the bad source line in the IDE.
     */
    void error(String message, Element element);
}
