package io.github.encapso.processor.validation;

import io.github.encapso.processor.domain.Reporter;
import io.github.encapso.processor.domain.ValidationContext;
import io.github.encapso.processor.domain.ValidationRule;
import io.github.encapso.processor.domain.ValidationScope;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

public class BoundaryTypeVisibilityRule implements ValidationRule {

    @Override
    public boolean validate(ValidationContext context, Reporter reporter) {
        boolean isValid = true;
        ExecutableElement methodElement = context.methodElement();
        TypeElement interfaceElement = context.interfaceElement();

        // 2.2: Parameters type are public
        for (VariableElement parameter : methodElement.getParameters()) {
            if (!checkTypeVisibility(parameter.asType(), interfaceElement, methodElement, "parameter", reporter)) {
                isValid = false;
            }
        }

        // 2.3: Return type is public
        if (!checkTypeVisibility(methodElement.getReturnType(), interfaceElement, methodElement, "return type", reporter)) {
            isValid = false;
        }

        // 2.4: Thrown exception is public
        for (TypeMirror thrownException : methodElement.getThrownTypes()) {
            if (!checkTypeVisibility(thrownException, interfaceElement, methodElement, "thrown exception", reporter)) {
                isValid = false;
            }
        }

        return isValid; }

    private boolean checkTypeVisibility(TypeMirror typeMirror, TypeElement interfaceElement, 
                                        ExecutableElement methodElement, String usageDesc, Reporter reporter) {
                                            
        if (typeMirror instanceof DeclaredType declaredType) {
            Element element = declaredType.asElement();
            if ((element.getKind().isClass() || element.getKind().isInterface()) &&
                    !element.getModifiers().contains(Modifier.PUBLIC) &&
                    !typeMirror.toString().startsWith("java.lang.") &&
                    !typeMirror.toString().startsWith("java.util.")) {

                String typeName = element.getSimpleName().toString();
                String interfaceName = interfaceElement.getSimpleName().toString();
                String methodName = methodElement.getSimpleName().toString();

                String errorMessage = String.format(
                        "The %s '%s' used in %s.%s() must be public because it is exposed through the component boundary.",
                        usageDesc, typeName, interfaceName, methodName);

                reporter.error(errorMessage, methodElement);
                return false;
            }
        }
        return true;
    }

    @Override
    public ValidationScope getScope() {
        return ValidationScope.METHOD;
    }
}
