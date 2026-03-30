package io.github.encapso.processor.domain;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Domain representation of the contextual metadata surrounding an AST operation.
 */
public record ValidationContext(
        TypeElement interfaceElement,
        ExecutableElement methodElement,
        TypeElement targetElement,
        String factoryMethodName,
        Elements elements,
        Types types,
        RoundEnvironment roundEnv
) {}
