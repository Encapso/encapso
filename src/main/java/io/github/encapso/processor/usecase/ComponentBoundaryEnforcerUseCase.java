package io.github.encapso.processor.usecase;

import io.github.encapso.processor.domain.BoundaryRegistry;
import io.github.encapso.processor.domain.Reporter;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.ElementScanner14;

/**
 * Scans every compiled root element for illegal references to internal component classes.
 *
 * <p>A reference is illegal when:
 * <ul>
 *   <li>The referenced type lives inside a component package</li>
 *   <li>The caller lives outside that component package</li>
 *   <li>The referenced type is NOT in the allowed set (interface, builder, signature types)</li>
 * </ul>
 */
public class ComponentBoundaryEnforcerUseCase {

    private final BoundaryRegistry registry;
    private final Reporter reporter;
    private final Elements elements;

    public ComponentBoundaryEnforcerUseCase(BoundaryRegistry registry, Reporter reporter,
                                             Elements elements) {
        this.registry = registry;
        this.reporter = reporter;
        this.elements = elements;
    }

    public void enforce(RoundEnvironment roundEnv) {
        if (registry.isEmpty()) return;

        BoundaryScanner scanner = new BoundaryScanner();
        for (Element rootElement : roundEnv.getRootElements()) {
            if (rootElement instanceof TypeElement typeElement) {
                String callerPackage = elements.getPackageOf(typeElement).getQualifiedName().toString();
                scanner.scan(typeElement, callerPackage);
            }
        }
    }

    /**
     * Standard visitor for traversing elements and checking declaration types.
     */
    private class BoundaryScanner extends ElementScanner14<Void, String> {

        @Override
        public Void visitType(TypeElement e, String callerPackage) {
            // Check the superclass (extends InternalClass)
            checkType(e.getSuperclass(), callerPackage, e);

            // Check implemented interfaces
            for (TypeMirror iface : e.getInterfaces()) {
                checkType(iface, callerPackage, e);
            }

            // Continue scanning members (fields, methods, etc.)
            return super.visitType(e, callerPackage);
        }

        @Override
        public Void visitVariable(VariableElement e, String callerPackage) {
            // Fields and parameters
            checkType(e.asType(), callerPackage, e);
            return super.visitVariable(e, callerPackage);
        }

        @Override
        public Void visitExecutable(ExecutableElement e, String callerPackage) {
            // Method return type
            checkType(e.getReturnType(), callerPackage, e);

            // Thrown exceptions
            for (TypeMirror thrown : e.getThrownTypes()) {
                checkType(thrown, callerPackage, e);
            }

            // Continue scanning (parameters)
            return super.visitExecutable(e, callerPackage);
        }

        private void checkType(TypeMirror mirror, String callerPackage, Element reportSite) {
            if (!(mirror instanceof DeclaredType declaredType)) return;

            if (declaredType.asElement() instanceof TypeElement referencedType) {
                registry.getViolatingComponentPackage(referencedType, callerPackage, elements).ifPresent(componentPkg ->
                        reporter.error(String.format(
                                "Class '%s' is an internal implementation detail of the '%s' component. " +
                                "It cannot be used directly outside the component. " +
                                "Use the component facade or its builder instead.",
                                referencedType.getSimpleName(), componentPkg),
                        reportSite));
            }

            // Also check generic type arguments (e.g. List<InternalType>)
            for (TypeMirror arg : declaredType.getTypeArguments()) {
                checkType(arg, callerPackage, reportSite);
            }
        }
    }
}
