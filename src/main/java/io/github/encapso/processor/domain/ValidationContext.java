package io.github.encapso.processor.domain;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * Domain representation of the contextual metadata surrounding an AST operation.
 */
public record ValidationContext(
        TypeElement interfaceElement,
        ExecutableElement methodElement,
        TypeElement targetElement,
        ProcessingEnvironment processingEnv
) {}
