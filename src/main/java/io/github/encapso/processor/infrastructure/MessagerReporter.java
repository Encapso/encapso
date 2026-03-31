package io.github.encapso.processor.infrastructure;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.Trees;
import io.github.encapso.processor.domain.Reporter;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

public class MessagerReporter implements Reporter {
    
    private final Messager messager;
    private final Trees trees;

    public MessagerReporter(Messager messager, Trees trees) {
        this.messager = messager;
        this.trees = trees;
    }

    @Override
    public void error(String message, Element element) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    @Override
    public void error(String message, Tree tree, CompilationUnitTree unit) {
        if (trees != null && tree != null && unit != null) {
            trees.printMessage(Diagnostic.Kind.ERROR, message, tree, unit);
        } else {
            messager.printMessage(Diagnostic.Kind.ERROR, message);
        }
    }

    @Override
    public void note(String message) {
        messager.printMessage(Diagnostic.Kind.NOTE, message);
    }
}
