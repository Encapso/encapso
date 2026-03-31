package io.github.encapso.processor.validation;

import io.github.encapso.processor.domain.Reporter;
import io.github.encapso.processor.domain.ValidationContext;
import io.github.encapso.processor.domain.ValidationRule;
import io.github.encapso.processor.domain.ValidationScope;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.List;

public class TargetMethodSignatureRule implements ValidationRule {

    @Override
    public boolean validate(ValidationContext context, Reporter reporter) {
        MatchResult result = findMatch(context);
        
        if (result == MatchResult.MATCH) return true;

        if (result == MatchResult.STATIC_FOUND) {
            reporter.error(String.format("The target method %s in class %s is static and cannot be used for delegation. Encapso only supports instance-based delegating.",
                    context.methodElement().getSimpleName(), context.targetElement().getSimpleName()), context.methodElement());
            return false;
        }

        String errorMessage = String.format(
                "The target class %s does not have a method matching the signature: %s",
                context.targetElement().getSimpleName(), context.methodElement().toString());
        reporter.error(errorMessage, context.methodElement());
        return false;
    }

    private enum MatchResult {
        NOT_FOUND,
        STATIC_FOUND,
        MATCH
    }

    private MatchResult findMatch(ValidationContext context) {
        boolean staticMatchFound = false;
        for (Element targetEnclosed : context.targetElement().getEnclosedElements()) {
            if (targetEnclosed instanceof ExecutableElement targetMethod) {
                if (isMethodSignatureMatch(context.methodElement(), targetMethod, context)) {
                    if (targetMethod.getModifiers().contains(Modifier.STATIC)) {
                        staticMatchFound = true;
                    } else {
                        return MatchResult.MATCH;
                    }
                }
            }
        }
        return staticMatchFound ? MatchResult.STATIC_FOUND : MatchResult.NOT_FOUND;
    }

    private boolean isMethodSignatureMatch(ExecutableElement sourceMethod, ExecutableElement targetMethod, ValidationContext context) {
        if (!targetMethod.getSimpleName().equals(sourceMethod.getSimpleName())) return false;

        javax.lang.model.util.Types types = context.types();

        // Resolve both source (facade) and target in their respective contexts (handling type variable substitution)
        javax.lang.model.type.DeclaredType sourceOwner = (javax.lang.model.type.DeclaredType) context.interfaceElement().asType();
        javax.lang.model.type.DeclaredType targetOwner = (javax.lang.model.type.DeclaredType) context.targetElement().asType();

        javax.lang.model.type.ExecutableType resolvedSource = (javax.lang.model.type.ExecutableType)
                types.asMemberOf(sourceOwner, sourceMethod);
        javax.lang.model.type.ExecutableType resolvedTarget = (javax.lang.model.type.ExecutableType)
                types.asMemberOf(targetOwner, targetMethod);

        if (!isTypeCompatible(resolvedSource.getReturnType(), resolvedTarget.getReturnType(), types)) {
            return false;
        }

        List<? extends TypeMirror> sourceParams = resolvedSource.getParameterTypes();
        List<? extends TypeMirror> targetParams = resolvedTarget.getParameterTypes();

        if (sourceParams.size() != targetParams.size()) return false;

        for (int i = 0; i < sourceParams.size(); i++) {
            if (!isTypeCompatible(sourceParams.get(i), targetParams.get(i), types)) {
                return false;
            }
        }

        List<? extends TypeMirror> sourceThrows = resolvedSource.getThrownTypes();
        List<? extends TypeMirror> targetThrows = resolvedTarget.getThrownTypes();

        for (TypeMirror targetThrow : targetThrows) {
            boolean found = false;
            for (TypeMirror sourceThrow : sourceThrows) {
                if (types.isAssignable(targetThrow, sourceThrow)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }

        return true;
    }

    private boolean isTypeCompatible(TypeMirror src, TypeMirror target, javax.lang.model.util.Types types) {
        if (types.isSameType(src, target)) return true;

        // If erasures match, we can bridge them with casts in the generated implementation.
        // This is crucial for handling complex generics and wildcards (? super T).
        if (types.isSameType(types.erasure(src), types.erasure(target))) {
            return true;
        }

        // Lenient matching for generics: if one is a TypeVariable, allow if it's assignable
        // to/from the other's erasure. 
        if (src.getKind() == javax.lang.model.type.TypeKind.TYPEVAR || 
            target.getKind() == javax.lang.model.type.TypeKind.TYPEVAR) {
            return types.isAssignable(src, types.erasure(target))
                || types.isAssignable(target, types.erasure(src));
        }

        return false;
    }

    @Override
    public ValidationScope getScope() {
        return ValidationScope.METHOD;
    }
}
