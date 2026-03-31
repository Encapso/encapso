package io.github.encapso.processor.validation;

import io.github.encapso.processor.domain.Reporter;
import io.github.encapso.processor.domain.ValidationContext;
import io.github.encapso.processor.domain.ValidationRule;
import io.github.encapso.processor.domain.ValidationScope;

import javax.lang.model.element.ElementKind;

/**
 * Validates that the @Component annotation is placed on an interface.
 * Blocks placement on classes, enums, records, etc.
 */
public class ComponentInterfaceRule implements ValidationRule {

    @Override
    public boolean validate(ValidationContext context, Reporter reporter) {
        ElementKind kind = context.interfaceElement().getKind();
        
        if (kind != ElementKind.INTERFACE) {
            String kindName = kind.name().toLowerCase().replace('_', ' ');
            String errorMessage = String.format(
                    "@Component can only be placed on interfaces. Found on %s: %s",
                    kindName, context.interfaceElement().getSimpleName());
            reporter.error(errorMessage, context.interfaceElement());
            return false;
        }
        
        return true;
    }

    @Override
    public ValidationScope getScope() {
        return ValidationScope.COMPONENT;
    }
}
