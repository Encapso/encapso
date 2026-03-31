package io.github.encapso.processor.usecase;

import io.github.encapso.engine.EncapsoEngine;
import io.github.encapso.engine.EncapsoType;
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
 * Javac implementation of the Encapso boundary enforcer.
 * This class serves as the 'Eyes' for the agnostic EncapsoEngine.
 */
public class ComponentBoundaryEnforcerUseCase {

    private final EncapsoEngine engine;
    private final Reporter reporter;
    private final Elements elements;
    private final Trees trees;

    public ComponentBoundaryEnforcerUseCase(EncapsoEngine engine, Reporter reporter,
                                             Elements elements, Trees trees) {
        this.engine = engine;
        this.reporter = reporter;
        this.elements = elements;
        this.trees = trees;
    }

    public void enforce(RoundEnvironment roundEnv) {
        BoundaryScanner elementScanner = new BoundaryScanner();
        SourceScanner treeScanner = new SourceScanner();
        java.util.Set<CompilationUnitTree> scannedUnits = new java.util.HashSet<>();

        for (Element rootElement : roundEnv.getRootElements()) {
            if (rootElement instanceof TypeElement typeElement) {
                elementScanner.scan(typeElement, typeElement);

                TreePath path = trees.getPath(typeElement);
                if (path != null) {
                    CompilationUnitTree unit = path.getCompilationUnit();
                    treeScanner.setUnit(unit);
                    if (scannedUnits.add(unit)) {
                        treeScanner.scan(unit, typeElement);
                    } else {
                        treeScanner.scan(path, typeElement);
                    }
                }
            }
        }
    }

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

            EncapsoType refType = toEncapsoType(importStr);
            engine.checkViolation(refType, callerPkg, callerFqn).ifPresent(v -> {
                reporter.error(formatViolation(v, refType, caller), node, currentUnit);
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
                checkTypeElement(typeElement, caller, tree, currentUnit);
            }
        }
    }

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
                checkTypeElement(referencedType, caller, null, null);
                for (TypeMirror arg : declaredType.getTypeArguments()) {
                    checkType(arg, caller, reportSite);
                }
            }
        }
    }

    private void checkTypeElement(TypeElement referencedType, TypeElement caller, Tree reportTree, CompilationUnitTree unit) {
        String callerPkg = elements.getPackageOf(caller).getQualifiedName().toString();
        String callerFqn = caller.getQualifiedName().toString();
        
        EncapsoType refType = new EncapsoType(referencedType.getQualifiedName().toString(), 
                                            elements.getPackageOf(referencedType).getQualifiedName().toString());

        engine.checkViolation(refType, callerPkg, callerFqn).ifPresent(v -> {
            String msg = formatViolation(v, refType, caller);
            if (reportTree != null && unit != null) {
                reporter.error(msg, reportTree, unit);
            } else {
                reporter.error(msg, caller);
            }
        });
    }

    private String formatViolation(EncapsoEngine.Violation v, EncapsoType refType, Element caller) {
        if (v.isSelfReference()) {
            return String.format(
                "Internal class '%s' cannot refer to its own component interface '%s'. " +
                "This maintains a strict architectural boundary between implementation and API.",
                caller.getSimpleName(), refType.simpleName());
        }
        
        if (refType.fqn().endsWith(".*")) {
            return String.format("Wildcard imports of component package '%s' are prohibited to prevent internal type leaks. " +
                "Use explicit imports for public types or add @Api to the internal type if it must be public.",
                v.componentPackage());
        }

        return String.format(
            "Class '%s' is an internal implementation detail of the '%s' component. " +
            "It cannot be used directly outside the component. " +
            "Use the @Component facade or its builder instead.",
            refType.simpleName(), v.componentPackage());
    }

    private EncapsoType toEncapsoType(String fqn) {
        String pkg = fqn.contains(".") ? fqn.substring(0, fqn.lastIndexOf('.')) : "";
        return new EncapsoType(fqn, pkg);
    }
}
