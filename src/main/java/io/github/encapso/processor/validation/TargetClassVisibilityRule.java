package io.github.encapso.processor.validation;

import io.github.encapso.processor.domain.Reporter;
import io.github.encapso.processor.domain.ValidationContext;
import io.github.encapso.processor.domain.ValidationRule;
import io.github.encapso.processor.domain.ValidationScope;

import javax.lang.model.element.Modifier;

public class TargetClassVisibilityRule implements ValidationRule {

    @Override
    public boolean validate(ValidationContext context, Reporter reporter) {
        String interfacePkg = context.elements().getPackageOf(context.interfaceElement()).getQualifiedName().toString();
        String targetPkg = context.elements().getPackageOf(context.targetElement()).getQualifiedName().toString();

        boolean isInsideComponent = targetPkg.equals(interfacePkg) || targetPkg.startsWith(interfacePkg + ".");

        // Rule 1.1: Is inside component
        if (!isInsideComponent) {
            String errorMessage = String.format(
                    "The target class %s must be inside the interface package %s or a subpackage.",
                    context.targetElement().getSimpleName(), interfacePkg);
            reporter.error(errorMessage, context.methodElement());
            return false;
        }

        // Rule 1.2: Is visible for the Component
        boolean isPublic = context.targetElement().getModifiers().contains(Modifier.PUBLIC);
        boolean isSamePackage = targetPkg.equals(interfacePkg);

        if (!isSamePackage && !isPublic) {
            String errorMessage = String.format(
                    "The target class %s is in a subpackage (%s) and must be public to be visible to the Component interface.",
                    context.targetElement().getSimpleName(), targetPkg);
            reporter.error(errorMessage, context.methodElement());
            return false;
        }

        return true;
    }

    @Override
    public ValidationScope getScope() {
        return ValidationScope.METHOD;
    }
}
