package io.github.encapso.processor.validation;

import io.github.encapso.processor.domain.Reporter;
import io.github.encapso.processor.domain.ValidationContext;
import io.github.encapso.processor.domain.ValidationRule;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.util.ElementFilter;
import java.util.List;

/**
 * Validates that the target class has a valid way to be instantiated:
 * - Either a public constructor
 * - Or a static factory method with a valid signature and visibility.
 */
public class TargetClassInstantiatorRule implements ValidationRule {

    @Override
    public boolean validate(ValidationContext context, Reporter reporter) {
        String factoryMethod = context.factoryMethodName();

        if (factoryMethod != null && !factoryMethod.isEmpty()) {
            return validateFactoryMethod(context, factoryMethod, reporter);
        } else {
            return validateConstructor(context, reporter);
        }
    }

    private boolean validateFactoryMethod(ValidationContext context, String methodName, Reporter reporter) {
        List<ExecutableElement> methods = ElementFilter.methodsIn(context.targetElement().getEnclosedElements()).stream()
                .filter(m -> m.getSimpleName().toString().equals(methodName))
                .toList();

        if (methods.isEmpty()) {
            reporter.error(String.format("Target class %s does not specify a factory method named '%s'",
                    context.targetElement().getSimpleName(), methodName), context.methodElement());
            return false;
        }

        // We check all methods with this name (overloads). At least one must be valid.
        boolean anyValid = false;
        for (ExecutableElement m : methods) {
            if (isValidFactoryMethod(m, context)) {
                anyValid = true;
                break;
            }
        }

        if (!anyValid) {
            reporter.error(String.format("Target class %s must have a 'static' factory method named '%s' that returns %s and is visible to the Component.",
                    context.targetElement().getSimpleName(), methodName, context.targetElement().getSimpleName()), context.methodElement());
            return false;
        }

        return true;
    }

    private boolean isValidFactoryMethod(ExecutableElement method, ValidationContext context) {
        // 1. Must be static
        if (!method.getModifiers().contains(Modifier.STATIC)) return false;

        // 2. Must return the target type (or a subtype)
        if (!context.types().isAssignable(method.getReturnType(), context.targetElement().asType())) return false;

        // 3. Must be visible (public or package-private if in same package)
        return isVisible(method, context);
    }

    private boolean validateConstructor(ValidationContext context, Reporter reporter) {
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(context.targetElement().getEnclosedElements());
        
        boolean anyVisible = constructors.stream().anyMatch(c -> isVisible(c, context));

        if (!anyVisible) {
            reporter.error(String.format("Target class %s must have a visible constructor (public or package-private if in the same package).",
                    context.targetElement().getSimpleName()), context.methodElement());
            return false;
        }

        return true;
    }

    private boolean isVisible(Element element, ValidationContext context) {
        if (element.getModifiers().contains(Modifier.PUBLIC)) return true;
        if (element.getModifiers().contains(Modifier.PRIVATE)) return false;
        if (element.getModifiers().contains(Modifier.PROTECTED)) return true; // Encapso handles protected via same-package generation or inheritance if possible, but usually same-package.

        // Package-private: only if in the same package as the component interface
        String componentPkg = context.elements().getPackageOf(context.interfaceElement()).getQualifiedName().toString();
        String elementPkg = context.elements().getPackageOf(element).getQualifiedName().toString();
        
        return componentPkg.equals(elementPkg);
    }
}
