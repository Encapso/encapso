package io.github.encapso.processor.infrastructure;

import io.github.encapso.processor.domain.Reporter;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

public class MessagerReporter implements Reporter {
    
    private final Messager messager;

    public MessagerReporter(Messager messager) {
        this.messager = messager;
    }

    @Override
    public void error(String message, Element element) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}
