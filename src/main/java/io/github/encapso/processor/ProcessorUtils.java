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
    public record DelegateRequest(TypeElement target, String factoryMethod) {}

    /**
     * Extracts the delegation request (target class + optional factory method) securely.
     */
    public static Optional<DelegateRequest> getDelegateRequest(ExecutableElement methodElement, Types types) {
        DelegateTo annotation = methodElement.getAnnotation(DelegateTo.class);
        if (annotation == null) return Optional.empty();

        return getTargetTypeMirror(annotation)
                .map(typeMirror -> (TypeElement) types.asElement(typeMirror))
                .map(target -> new DelegateRequest(target, annotation.factoryMethod()));
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

    /**
     * Checks if an element (method or constructor) is visible to the component interface.
     * Visibility rules:
     * 1. Public is always visible.
     * 2. Private is never visible.
     * 3. Protected/Package-private are visible only if in the same package as the interface.
     */
    public static boolean isVisible(javax.lang.model.element.Element element, 
                                    javax.lang.model.element.TypeElement interfaceElement, 
                                    javax.lang.model.util.Elements elements) {
        java.util.Set<javax.lang.model.element.Modifier> modifiers = element.getModifiers();
        if (modifiers.contains(javax.lang.model.element.Modifier.PUBLIC)) return true;
        if (modifiers.contains(javax.lang.model.element.Modifier.PRIVATE)) return false;

        // Protected or Package-private: only if in the same package
        String interfacePkg = elements.getPackageOf(interfaceElement).getQualifiedName().toString();
        String elementPkg = elements.getPackageOf(element).getQualifiedName().toString();
        return interfacePkg.equals(elementPkg);
    }

    /**
     * Checks if the element is annotated with jakarta.inject.Inject.
     */
    public static boolean isInjectAnnotated(javax.lang.model.element.Element element) {
        for (javax.lang.model.element.AnnotationMirror am : element.getAnnotationMirrors()) {
            String fqn = am.getAnnotationType().asElement().toString();
            if (fqn.equals("jakarta.inject.Inject")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts a TypeMirror to a TypeElement if possible.
     */
    public static javax.lang.model.element.TypeElement toTypeElement(javax.lang.model.type.TypeMirror mirror) {
        if (mirror instanceof javax.lang.model.type.DeclaredType dt && 
            dt.asElement() instanceof javax.lang.model.element.TypeElement te) {
            return te;
        }
        return null;
    }

    /**
     * Converts a string to camelCase.
     */
    public static String camelCase(String name) {
        if (name == null || name.isEmpty()) return name;
        if (name.length() == 1) return name.toLowerCase();
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
