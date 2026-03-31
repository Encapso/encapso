package io.github.encapso.processor.validation;

import io.github.encapso.processor.domain.Reporter;
import io.github.encapso.processor.domain.ValidationContext;
import io.github.encapso.processor.domain.ValidationRule;
import io.github.encapso.processor.domain.ValidationScope;

import javax.lang.model.element.TypeElement;

/**
 * Ensures that @Component interfaces do not extend any other interfaces.
 * This rule promotes a flat, composition-based component model and keeps
 * boundaries explicit.
 */
public class ComponentInterfaceHierarchyRule implements ValidationRule {

    @Override
    public boolean validate(ValidationContext context, Reporter reporter) {
        TypeElement interfaceElement = context.interfaceElement();

        if (!interfaceElement.getInterfaces().isEmpty()) {
            String errorMessage = String.format(
                    "The @Component interface %s cannot extend other interfaces. " +
                    "Keep components flat and use composition for internal logic.",
                    interfaceElement.getSimpleName()
            );
            // We report on the interface itself
            reporter.error(errorMessage, interfaceElement);
            return false;
        }

        return true;
    }

    @Override
    public ValidationScope getScope() {
        return ValidationScope.COMPONENT;
    }
}
