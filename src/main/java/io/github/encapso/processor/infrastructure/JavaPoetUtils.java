package io.github.encapso.processor.infrastructure;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared utility methods for JavaPoet-based code generation.
 */
public final class JavaPoetUtils {

    private JavaPoetUtils() {
    }

    /**
     * Converts a {@link TypeMirror} to a {@link TypeElement} if possible.
     */
    public static TypeElement toTypeElement(TypeMirror mirror) {
        if (mirror instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) {
            return te;
        }
        return null;
    }

    /**
     * Checks if a {@link TypeName} or any of its components contains a type variable.
     */
    public static boolean containsTypeVariable(TypeName typeName) {
        if (typeName instanceof TypeVariableName) {
            return true;
        }
        if (typeName instanceof ParameterizedTypeName ptn) {
            return ptn.typeArguments.stream().anyMatch(JavaPoetUtils::containsTypeVariable);
        }
        if (typeName instanceof WildcardTypeName wtn) {
            return wtn.upperBounds.stream().anyMatch(JavaPoetUtils::containsTypeVariable) ||
                    wtn.lowerBounds.stream().anyMatch(JavaPoetUtils::containsTypeVariable);
        }
        if (typeName instanceof ArrayTypeName atn) {
            return containsTypeVariable(atn.componentType);
        }
        return false;
    }

    /**
     * Recursively replaces type variables in a {@link TypeName} with name-only versions.
     * This is useful to avoid re-declaring bounds in method signatures or fields.
     */
    public static TypeName replaceTypeVariablesWithNameOnly(TypeName typeName) {
        if (typeName instanceof TypeVariableName tv) {
            return TypeVariableName.get(tv.name);
        } else if (typeName instanceof ParameterizedTypeName ptn) {
            List<TypeName> newArgs = new ArrayList<>();
            for (TypeName arg : ptn.typeArguments) {
                newArgs.add(replaceTypeVariablesWithNameOnly(arg));
            }
            return ParameterizedTypeName.get(ptn.rawType, newArgs.toArray(new TypeName[0]));
        } else if (typeName instanceof WildcardTypeName wtn) {
            if (!wtn.lowerBounds.isEmpty()) {
                return WildcardTypeName.supertypeOf(replaceTypeVariablesWithNameOnly(wtn.lowerBounds.get(0)));
            } else if (!wtn.upperBounds.isEmpty() && !wtn.upperBounds.get(0).equals(TypeName.OBJECT)) {
                return WildcardTypeName.subtypeOf(replaceTypeVariablesWithNameOnly(wtn.upperBounds.get(0)));
            }
            return wtn;
        } else if (typeName instanceof ArrayTypeName atn) {
            return ArrayTypeName.of(replaceTypeVariablesWithNameOnly(atn.componentType));
        }
        return typeName;
    }
}
