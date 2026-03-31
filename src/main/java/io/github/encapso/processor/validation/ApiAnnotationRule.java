package io.github.encapso.processor.validation;

import io.github.encapso.Component;
import io.github.encapso.processor.domain.Reporter;
import io.github.encapso.processor.domain.ValidationContext;
import io.github.encapso.processor.domain.ValidationRule;
import io.github.encapso.processor.domain.ValidationScope;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.Set;

/**
 * Validates that the @Api annotation is used correctly:
 * - Annotated type must be public.
 * - Must be located within a package managed by a @Component.
 */
public class ApiAnnotationRule implements ValidationRule {

    @Override
    public boolean validate(ValidationContext context, Reporter reporter) {
        // In the context of @Api validation, context.interfaceElement() might be null 
        // if we are validating @Api standalone, or non-null if we are validating 
        // during a component scan.
        
        // However, for V1.0, we will validate @Api elements directly.
        // We'll assume the 'targetElement' is the one annotated with @Api.
        
        TypeElement apiElement = context.targetElement();
        if (apiElement == null) return true;

        // 1. Must be public
        if (!apiElement.getModifiers().contains(Modifier.PUBLIC)) {
            reporter.error("@Api annotation can only be used on public types (classes, interfaces, or enums).", apiElement);
            return false;
        }

        // 2. Must be inside a @Component package
        if (!isInsideComponentPackage(apiElement, context)) {
            reporter.error("@Api annotated type must be located within a package (or subpackage) that is managed by a @Component.", apiElement);
            return false;
        }

        return true;
    }

    private boolean isInsideComponentPackage(TypeElement element, ValidationContext context) {
        String elementPkg = context.elements().getPackageOf(element).getQualifiedName().toString();
        
        Set<? extends Element> components = context.roundEnv().getElementsAnnotatedWith(Component.class);
        for (Element comp : components) {
            String compPkg = context.elements().getPackageOf(comp).getQualifiedName().toString();
            if (elementPkg.equals(compPkg) || elementPkg.startsWith(compPkg + ".")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ValidationScope getScope() {
        return ValidationScope.METHOD;
    }
}
