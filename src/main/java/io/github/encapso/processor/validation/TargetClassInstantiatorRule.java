package io.github.encapso.processor.validation;

import io.github.encapso.processor.ProcessorUtils;
import io.github.encapso.processor.domain.Reporter;
import io.github.encapso.processor.domain.ValidationContext;
import io.github.encapso.processor.domain.ValidationRule;
import io.github.encapso.processor.domain.ValidationScope;

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
            // Rule 2.1: Target must be concrete for constructor instantiation
            if (context.targetElement().getModifiers().contains(Modifier.ABSTRACT) || 
                context.targetElement().getKind().isInterface()) {
                reporter.error(String.format("Target class %s is abstract or an interface and cannot be instantiated. Please provide a concrete class or a static factory method.",
                        context.targetElement().getSimpleName()), context.methodElement());
                return false;
            }
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

        // Check all methods with this name (overloads)
        List<ExecutableElement> validOverloads = methods.stream()
                .filter(m -> isValidFactoryMethod(m, context))
                .toList();

        if (validOverloads.isEmpty()) {
            reporter.error(String.format("Target class %s must have a 'static' factory method named '%s' that returns %s and is visible to the Component.",
                    context.targetElement().getSimpleName(), methodName, context.targetElement().getSimpleName()), context.methodElement());
            return false;
        }

        // Check for @Inject annotations among valid overloads
        List<ExecutableElement> injectMethods = validOverloads.stream()
                .filter(ProcessorUtils::isInjectAnnotated)
                .toList();

        if (injectMethods.size() > 1) {
            reporter.error(String.format("Multiple @Inject factory methods named '%s' found in %s. Only one may be annotated with @Inject.",
                    methodName, context.targetElement().getSimpleName()), context.methodElement());
            return false;
        }

        if (injectMethods.size() == 1) {
            return true; // Single @Inject is always preferred
        }

        // Fall back to max parameters heuristic if no @Inject
        int maxParams = validOverloads.stream()
                .mapToInt(m -> m.getParameters().size())
                .max()
                .getAsInt();

        List<ExecutableElement> candidates = validOverloads.stream()
                .filter(m -> m.getParameters().size() == maxParams)
                .toList();

        if (candidates.size() > 1) {
            reporter.error(String.format("Ambiguous factory method '%s' in %s: Found %d overloads with the same maximum parameter count (%d). Please annotate the intended method with @Inject to resolve the ambiguity.",
                    methodName, context.targetElement().getSimpleName(), candidates.size(), maxParams), context.methodElement());
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
        return ProcessorUtils.isVisible(method, context.interfaceElement(), context.elements());
    }

    private boolean validateConstructor(ValidationContext context, Reporter reporter) {
        List<ExecutableElement> visibleConstructors = ElementFilter.constructorsIn(context.targetElement().getEnclosedElements()).stream()
                .filter(c -> ProcessorUtils.isVisible(c, context.interfaceElement(), context.elements()))
                .toList();

        if (visibleConstructors.isEmpty()) {
            reporter.error(String.format("Target class %s must have a visible constructor (public or package-private if in the same package).",
                    context.targetElement().getSimpleName()), context.methodElement());
            return false;
        }

        // Check for @Inject annotations
        List<ExecutableElement> injectConstructors = visibleConstructors.stream()
                .filter(ProcessorUtils::isInjectAnnotated)
                .toList();

        if (injectConstructors.size() > 1) {
            reporter.error(String.format("Multiple @Inject constructors found in %s. Only one constructor may be annotated with @Inject.",
                    context.targetElement().getSimpleName()), context.methodElement());
            return false;
        }

        if (injectConstructors.size() == 1) {
            return true; // Single @Inject is always preferred
        }

        // Fall back to max parameter count heuristic if no @Inject
        int maxParams = visibleConstructors.stream()
                .mapToInt(c -> c.getParameters().size())
                .max()
                .getAsInt();

        List<ExecutableElement> candidates = visibleConstructors.stream()
                .filter(c -> c.getParameters().size() == maxParams)
                .toList();

        if (candidates.size() > 1) {
            reporter.error(String.format("Ambiguous constructors in %s: Found %d visible constructors with the same maximum parameter count (%d). Please annotate the intended constructor with @Inject to resolve the ambiguity.",
                    context.targetElement().getSimpleName(), candidates.size(), maxParams), context.methodElement());
            return false;
        }

        return true;
    }


    @Override
    public ValidationScope getScope() {
        return ValidationScope.METHOD;
    }
}
