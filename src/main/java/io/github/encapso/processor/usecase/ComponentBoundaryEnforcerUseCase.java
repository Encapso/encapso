package io.github.encapso.processor.usecase;

import io.github.encapso.processor.domain.BoundaryRegistry;
import io.github.encapso.processor.domain.Reporter;
import com.sun.source.tree.*;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

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
 *   <li>The referenced type is NOT in the allowed set (interface, builder, signature types, @Api)</li>
 * </ul>
 */
public class ComponentBoundaryEnforcerUseCase {

    private final BoundaryRegistry registry;
    private final Reporter reporter;
    private final Elements elements;
    private final Trees trees;

    public ComponentBoundaryEnforcerUseCase(BoundaryRegistry registry, Reporter reporter,
                                             Elements elements, Trees trees) {
        this.registry = registry;
        this.reporter = reporter;
        this.elements = elements;
        this.trees = trees;
    }

    public void enforce(RoundEnvironment roundEnv) {
        if (registry.isEmpty()) return;

        BoundaryScanner elementScanner = new BoundaryScanner();
        SourceScanner treeScanner = new SourceScanner();
        java.util.Set<com.sun.source.tree.CompilationUnitTree> scannedUnits = new java.util.HashSet<>();

        for (Element rootElement : roundEnv.getRootElements()) {
            if (rootElement instanceof TypeElement typeElement) {
                reporter.note("Scanning: " + typeElement.getQualifiedName());
                // 1. Scan Element declarations (fields, method signatures)
                elementScanner.scan(typeElement, typeElement);

                // 2. Scan AST implementations (method bodies, local vars)
                TreePath path = trees.getPath(typeElement);
                if (path != null) {
                    com.sun.source.tree.CompilationUnitTree unit = path.getCompilationUnit();
                    treeScanner.setUnit(unit);
                    if (scannedUnits.add(unit)) {
                        // Scan entire file (includes imports)
                        treeScanner.scan(unit, typeElement);
                    } else {
                        // Just scan this class body (redundant but safe context switch)
                        treeScanner.scan(path, typeElement);
                    }
                }
            }
        }
    }

    /**
     * Tree scanner for inspecting the source implementation (method bodies, etc)
     */
    private class SourceScanner extends TreePathScanner<Void, TypeElement> {
        private CompilationUnitTree currentUnit;

        public void setUnit(CompilationUnitTree unit) {
            this.currentUnit = unit;
        }
        
        @Override
        public Void visitImport(ImportTree node, TypeElement caller) {
            String importStr = node.getQualifiedIdentifier().toString();
            String callerPkg = elements.getPackageOf(caller).getQualifiedName().toString();
            String callerFqn = caller.getQualifiedName().toString();

            registry.getViolatingComponentPackageForFqn(importStr, callerPkg, callerFqn, elements)
                .ifPresent(violation -> {
                    String msg;
                    if (importStr.endsWith(".*")) {
                        msg = String.format("Wildcard imports of component package '%s' are prohibited to prevent internal type leaks. " +
                            "Use explicit imports for public types or add @Api to the internal type if it must be public.",
                            violation.componentPackage());
                    } else {
                        String simpleName = importStr.contains(".") ? importStr.substring(importStr.lastIndexOf('.') + 1) : importStr;
                        msg = String.format("Class '%s' is an internal implementation detail of the '%s' component. " +
                            "It cannot be used directly outside the component. " +
                            "Use the @Component facade or its builder instead.",
                            simpleName, violation.componentPackage());
                    }
                    reporter.error(msg, node, currentUnit);
                });

            return super.visitImport(node, caller);
        }

        @Override
        public Void visitVariable(VariableTree node, TypeElement caller) {
            checkTree(node.getType(), caller);
            return super.visitVariable(node, caller);
        }

        @Override
        public Void visitNewClass(NewClassTree node, TypeElement caller) {
            checkTree(node.getIdentifier(), caller);
            return super.visitNewClass(node, caller);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree node, TypeElement caller) {
            checkTree(node, caller);
            return super.visitMemberSelect(node, caller);
        }

        @Override
        public Void visitIdentifier(IdentifierTree node, TypeElement caller) {
            checkTree(node, caller);
            return super.visitIdentifier(node, caller);
        }

        private void checkTree(Tree tree, TypeElement caller) {
            if (tree == null) return;
            Element element = trees.getElement(new TreePath(getCurrentPath(), tree));
            if (element instanceof TypeElement typeElement) {
                checkTypeElement(typeElement, caller, caller, tree, currentUnit);
            }
        }
    }

    /**
     * Standard visitor for traversing elements and checking declaration types.
     */
    private class BoundaryScanner extends ElementScanner14<Void, TypeElement> {

        @Override
        public Void visitType(TypeElement e, TypeElement caller) {
            checkType(e.getSuperclass(), caller, e);
            for (TypeMirror iface : e.getInterfaces()) {
                checkType(iface, caller, e);
            }
            return super.visitType(e, caller);
        }

        @Override
        public Void visitVariable(VariableElement e, TypeElement caller) {
            checkType(e.asType(), caller, e);
            return super.visitVariable(e, caller);
        }

        @Override
        public Void visitExecutable(ExecutableElement e, TypeElement caller) {
            checkType(e.getReturnType(), caller, e);
            for (TypeMirror thrown : e.getThrownTypes()) {
                checkType(thrown, caller, e);
            }
            return super.visitExecutable(e, caller);
        }

        private void checkType(TypeMirror mirror, TypeElement caller, Element reportSite) {
            if (!(mirror instanceof DeclaredType declaredType)) return;
            if (declaredType.asElement() instanceof TypeElement referencedType) {
                checkTypeElement(referencedType, caller, reportSite, null, null);
                for (TypeMirror arg : declaredType.getTypeArguments()) {
                    checkType(arg, caller, reportSite);
                }
            }
        }
    }

    /**
     * Unified logic for checking if a referenced type element violates component encapsulation.
     */
    private void checkTypeElement(TypeElement referencedType, TypeElement caller, Element reportSite, Tree reportTree, CompilationUnitTree unit) {
        String callerPackage = elements.getPackageOf(caller).getQualifiedName().toString();
        String callerFqn = caller.getQualifiedName().toString();
        String refFqn = referencedType.getQualifiedName().toString();

        registry.getViolatingComponentPackageForFqn(refFqn, callerPackage, callerFqn, elements).ifPresent(violation -> {
            String msg;
            if (violation.isSelfReference()) {
                msg = String.format(
                    "Internal class '%s' cannot refer to its own component interface '%s'. " +
                    "This maintains a strict architectural boundary between implementation and API.",
                    getEnclosingType(reportSite).getSimpleName(),
                    referencedType.getSimpleName());
            } else {
                msg = String.format(
                    "Class '%s' is an internal implementation detail of the '%s' component. " +
                    "It cannot be used directly outside the component. " +
                    "Use the @Component facade or its builder instead.",
                    referencedType.getSimpleName(), violation.componentPackage());
            }

            if (reportTree != null && unit != null) {
                reporter.error(msg, reportTree, unit);
            } else {
                reporter.error(msg, reportSite);
            }
        });
    }

    private Element getEnclosingType(Element e) {
        while (e != null && !(e instanceof TypeElement)) {
            e = e.getEnclosingElement();
        }
        return e;
    }
}
