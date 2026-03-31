package io.github.encapso.processor.domain;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import javax.lang.model.element.Element;

/**
 * Agnostic port to decouple Java compilation console error reporting.
 */
public interface Reporter {
    
    /**
     * Prints a fatal compiler-crashing error binding precisely to the bad source line in the IDE.
     */
    void error(String message, Element element);

    /**
     * Prints a fatal compiler-crashing error binding precisely to a specific line in the source tree.
     */
    void error(String message, Tree tree, CompilationUnitTree unit);

    /**
     * Prints a non-fatal note in the compiler output.
     */
    void note(String message);
}
