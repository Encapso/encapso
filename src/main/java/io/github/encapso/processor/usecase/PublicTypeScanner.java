package io.github.encapso.processor.usecase;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Scans a @Component and its package tree for "public" types allowed externally.
 * Collects types from method signatures and types annotated with @Api.
 */
public class PublicTypeScanner {

    private final Elements elements;

    public PublicTypeScanner(Elements elements) {
        this.elements = elements;
    }

    public Set<String> scan(TypeElement interfaceElement, String componentPackage, RoundEnvironment roundEnv) {
        Set<String> allowedTypes = new LinkedHashSet<>();

        // 1. The interface and its generated builder are always allowed
        allowedTypes.add(interfaceElement.getQualifiedName().toString());
        allowedTypes.add(componentPackage + "." + interfaceElement.getSimpleName() + "Builder");

        // 2. Collect types from method signatures
        scanMethods(interfaceElement, allowedTypes);

        // 3. Collect @Api annotated types in the package tree
        scanApiAnnotations(componentPackage, roundEnv, allowedTypes);

        return allowedTypes;
    }

    private void scanMethods(TypeElement interfaceElement, Set<String> allowed) {
        for (Element member : interfaceElement.getEnclosedElements()) {
            if (member instanceof ExecutableElement method) {
                collectRecursive(method.getReturnType(), allowed);
                method.getParameters().forEach(p -> collectRecursive(p.asType(), allowed));
                method.getThrownTypes().forEach(t -> collectRecursive(t, allowed));
            }
        }
    }

    private void scanApiAnnotations(String componentPackage, RoundEnvironment roundEnv, Set<String> allowed) {
        TypeElement apiAnnotation = elements.getTypeElement("io.github.encapso.Api");
        if (apiAnnotation == null) return;

        for (Element apiElement : roundEnv.getElementsAnnotatedWith(apiAnnotation)) {
            if (apiElement instanceof TypeElement te) {
                String tePackage = elements.getPackageOf(te).getQualifiedName().toString();
                if (tePackage.equals(componentPackage) || tePackage.startsWith(componentPackage + ".")) {
                    allowed.add(te.getQualifiedName().toString());
                }
            }
        }
    }

    private void collectRecursive(TypeMirror mirror, Set<String> allowed) {
        if (mirror instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) {
            allowed.add(te.getQualifiedName().toString());
            dt.getTypeArguments().forEach(arg -> collectRecursive(arg, allowed));
        } else if (mirror instanceof WildcardType wt) {
            if (wt.getExtendsBound() != null) collectRecursive(wt.getExtendsBound(), allowed);
            if (wt.getSuperBound() != null) collectRecursive(wt.getSuperBound(), allowed);
        } else if (mirror instanceof ArrayType at) {
            collectRecursive(at.getComponentType(), allowed);
        }
    }
}
