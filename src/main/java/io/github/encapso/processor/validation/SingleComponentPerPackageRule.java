package io.github.encapso.processor.validation;

import io.github.encapso.Component;
import io.github.encapso.processor.domain.Reporter;
import io.github.encapso.processor.domain.ValidationContext;
import io.github.encapso.processor.domain.ValidationRule;
import io.github.encapso.processor.domain.ValidationScope;

import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enforces that a single package contains at most one @Component interface.
 * This ensures each package has a clear entry point and isolation boundary.
 */
public class SingleComponentPerPackageRule implements ValidationRule {

    @Override
    public boolean validate(ValidationContext context, Reporter reporter) {
        TypeElement interfaceElement = context.interfaceElement();
        String pkgName = context.elements().getPackageOf(interfaceElement).getQualifiedName().toString();

        List<String> otherComponents = context.roundEnv().getElementsAnnotatedWith(Component.class).stream()
                .filter(e -> e instanceof TypeElement te && !te.equals(interfaceElement))
                .filter(e -> context.elements().getPackageOf(e).getQualifiedName().toString().equals(pkgName))
                .map(e -> ((TypeElement) e).getSimpleName().toString())
                .sorted()
                .collect(Collectors.toList());

        if (!otherComponents.isEmpty()) {
            String otherNames = String.join(", ", otherComponents);
            
            reporter.error(String.format(
                    "Package '%s' contains multiple @Component interfaces: [%s, %s]. " +
                    "Only one component is allowed per package to maintain clear boundaries. " +
                    "Please move additional components to sub-packages or different package trees.",
                    pkgName, interfaceElement.getSimpleName(), otherNames), interfaceElement);
            return false;
        }

        return true;
    }

    @Override
    public ValidationScope getScope() {
        return ValidationScope.COMPONENT;
    }
}
