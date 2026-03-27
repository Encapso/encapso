package io.github.encapso.processor.validation;

import io.github.encapso.processor.domain.Reporter;
import io.github.encapso.processor.domain.ValidationContext;
import io.github.encapso.processor.domain.ValidationRule;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

public class TargetMethodSignatureRule implements ValidationRule {

    @Override
    public boolean validate(ValidationContext context, Reporter reporter) {
        if (!hasMatchingMethodSignature(context)) {
            String errorMessage = String.format(
                    "The target class %s does not have a method matching the signature: %s",
                    context.targetElement().getSimpleName(), context.methodElement().toString());
            reporter.error(errorMessage, context.methodElement());
            return false;
        }
        return true;
    }

    private boolean hasMatchingMethodSignature(ValidationContext context) {
        for (Element targetEnclosed : context.targetElement().getEnclosedElements()) {
            if (targetEnclosed instanceof ExecutableElement targetMethod) {
                if (isMethodSignatureMatch(context.methodElement(), targetMethod, context)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isMethodSignatureMatch(ExecutableElement sourceMethod, ExecutableElement targetMethod, ValidationContext context) {
        if (!targetMethod.getSimpleName().equals(sourceMethod.getSimpleName())) return false;

        if (!context.processingEnv().getTypeUtils().isSameType(sourceMethod.getReturnType(), targetMethod.getReturnType())) {
            return false;
        }

        List<? extends VariableElement> sourceParams = sourceMethod.getParameters();
        List<? extends VariableElement> targetParams = targetMethod.getParameters();

        if (sourceParams.size() != targetParams.size()) return false;

        for (int i = 0; i < sourceParams.size(); i++) {
            TypeMirror sourceParamType = sourceParams.get(i).asType();
            TypeMirror targetParamType = targetParams.get(i).asType();
            if (!context.processingEnv().getTypeUtils().isSameType(sourceParamType, targetParamType)) {
                return false;
            }
        }

        List<? extends TypeMirror> sourceThrows = sourceMethod.getThrownTypes();
        List<? extends TypeMirror> targetThrows = targetMethod.getThrownTypes();

        if (sourceThrows.size() != targetThrows.size()) return false;

        for (TypeMirror sourceThrow : sourceThrows) {
            boolean found = false;
            for (TypeMirror targetThrow : targetThrows) {
                if (context.processingEnv().getTypeUtils().isSameType(sourceThrow, targetThrow)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }

        return true;
    }
}
