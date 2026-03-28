package io.github.encapso.processor;

import io.github.encapso.DelegateTo;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.Optional;

public class ProcessorUtils {

    /**
     * Extracts the class type securely since it is inside an annotation
     * where the target class has potentially not been loaded natively in the
     * compiler.
     */
    public static Optional<TypeElement> getDelegateTargetElement(ExecutableElement methodElement, Types types) {
        return Optional.ofNullable(methodElement.getAnnotation(DelegateTo.class))
                .flatMap(ProcessorUtils::getTargetTypeMirror)
                .map(typeMirror -> (TypeElement) types.asElement(typeMirror));
    }

    /**
     * Extracts the TypeMirror from the @DelegateTo annotation safely.
     * When reading Class<?> attributes from annotations during compile-time,
     * a MirroredTypeException is strictly thrown if the exact class isn't fully compiled into this ClassLoader.
     */
    private static Optional<TypeMirror> getTargetTypeMirror(DelegateTo delegateTo) {
        try {
            delegateTo.value();
            return Optional.empty();
        } catch (MirroredTypeException e) {
            return Optional.ofNullable(e.getTypeMirror());
        }
    }
}
