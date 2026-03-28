package io.github.encapso.processor.infrastructure;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import io.github.encapso.processor.ComponentProcessor;
import io.github.encapso.processor.domain.DependencyGraph;
import io.github.encapso.processor.domain.FacadeGenerator;

import javax.annotation.processing.Generated;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Map;

public class JavaPoetFacadeGenerator implements FacadeGenerator {

    @Override
    public void generateFacade(TypeElement interfaceElement,
                               Map<ExecutableElement, TypeElement> delegateMapping,
                               GeneratorContext context) {
        String packageName = context.getPackageName(interfaceElement);
        String generatedClassName = interfaceElement.getSimpleName() + "Impl";

        TypeSpec classSpec = buildClass(interfaceElement, generatedClassName, delegateMapping, context);

        try {
            JavaFile.builder(packageName, classSpec).indent("    ").build()
                    .writeTo(context.filer());
        } catch (IOException e) {
            context.messager().printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate Facade: " + e.getMessage());
        }
    }

    private TypeSpec buildClass(TypeElement interfaceElement, String generatedClassName,
                                Map<ExecutableElement, TypeElement> delegateMapping,
                                GeneratorContext context) {
        DependencyGraph graph = context.graph();
        Types types = context.types();
        // Handle generic type variables from the interface
        List<TypeVariableName> typeVariables = interfaceElement.getTypeParameters().stream()
                .map(TypeVariableName::get)
                .toList();

        // Package-private class — no Modifier.PUBLIC
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(generatedClassName)
                .addModifiers(Modifier.FINAL)
                .addTypeVariables(typeVariables)
                .addSuperinterface(TypeName.get(interfaceElement.asType()))
                .addAnnotation(AnnotationSpec.builder(Generated.class)
                        .addMember("value", "$S", ComponentProcessor.class.getCanonicalName())
                        .build());

        Map<TypeElement, String> tcNames = graph.tcInstanceNames();

        // Fields — one per unique TC, named from DependencyGraph (package-private)
        for (Map.Entry<TypeElement, String> entry : tcNames.entrySet()) {
            ClassName fieldType = ClassName.get(entry.getKey());
            classBuilder.addField(
                    FieldSpec.builder(fieldType, entry.getValue())
                            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                            .build());
        }

        // Package-private constructor — takes TCs in graph order
        MethodSpec.Builder ctor = MethodSpec.constructorBuilder(); // no Modifier.PUBLIC
        for (Map.Entry<TypeElement, String> entry : tcNames.entrySet()) {
            ClassName paramType = ClassName.get(entry.getKey());
            ctor.addParameter(paramType, entry.getValue());
            ctor.addStatement("this.$N = $N", entry.getValue(), entry.getValue());
        }
        classBuilder.addMethod(ctor.build());

        // Delegate methods
        javax.lang.model.type.DeclaredType owner = (javax.lang.model.type.DeclaredType) interfaceElement.asType();

        for (Map.Entry<ExecutableElement, TypeElement> entry : delegateMapping.entrySet()) {
            ExecutableElement facadeMethod = entry.getKey();
            TypeElement targetElement = entry.getValue();
            String fieldName = tcNames.get(targetElement);

            // Find the matching target method again to get its parameter types for casting
            ExecutableElement targetMethod = findMatchingTargetMethod(facadeMethod, targetElement);
            
            // overriding(ExecutableElement, DeclaredType, Types) handles type substitution
            MethodSpec.Builder methodBuilder = MethodSpec.overriding(facadeMethod, owner, types);
            
            javax.lang.model.type.ExecutableType resolvedSource = (javax.lang.model.type.ExecutableType) types.asMemberOf(owner, facadeMethod);
            javax.lang.model.type.ExecutableType resolvedTarget = (javax.lang.model.type.ExecutableType) types.asMemberOf((javax.lang.model.type.DeclaredType) targetElement.asType(), targetMethod);

            List<Object> args = new java.util.ArrayList<>();
            StringBuilder format = new StringBuilder();
            
            for (int i = 0; i < facadeMethod.getParameters().size(); i++) {
                if (i > 0) format.append(", ");
                
                TypeMirror sourceParamType = resolvedSource.getParameterTypes().get(i);
                TypeMirror targetParamType = resolvedTarget.getParameterTypes().get(i);

                if (types.isSameType(sourceParamType, targetParamType)) {
                    format.append("$N");
                    args.add(facadeMethod.getParameters().get(i).getSimpleName());
                } else {
                    format.append("($T) $N");
                    args.add(TypeName.get(types.erasure(targetParamType)));
                    args.add(facadeMethod.getParameters().get(i).getSimpleName());
                }
            }

            if (facadeMethod.getReturnType().getKind().name().equals("VOID")) {
                List<Object> statementArgs = new java.util.ArrayList<>();
                statementArgs.add(fieldName);
                statementArgs.add(facadeMethod.getSimpleName());
                statementArgs.addAll(args);
                methodBuilder.addStatement("this.$N.$N(" + format + ")", statementArgs.toArray());
            } else {
                TypeName facadeReturnType = TypeName.get(resolvedSource.getReturnType());
                TypeName targetReturnType = TypeName.get(resolvedTarget.getReturnType());

                List<Object> statementArgs = new java.util.ArrayList<>();
                String stmtFormat;

                // Even if types are "equal", we need a cast if the return type involves type variables
                // because the delegate field is raw, so the result of the call will be the erasure.
                if (facadeReturnType.equals(targetReturnType) && !containsTypeVariable(targetReturnType)) {
                    stmtFormat = "return this.$N.$N(" + format + ")";
                } else {
                    stmtFormat = "return ($T) this.$N.$N(" + format + ")";
                    statementArgs.add(facadeReturnType);

                    // If we are casting to a type variable, we need to suppress unchecked warnings
                    if (containsTypeVariable(facadeReturnType)) {
                        methodBuilder.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                                .addMember("value", "$S", "unchecked")
                                .build());
                    }
                }
                statementArgs.add(fieldName);
                statementArgs.add(facadeMethod.getSimpleName());
                statementArgs.addAll(args);

                methodBuilder.addStatement(stmtFormat, statementArgs.toArray());
            }
            classBuilder.addMethod(methodBuilder.build());
        }

        return classBuilder.build();
    }

    private boolean containsTypeVariable(TypeName typeName) {
        return JavaPoetUtils.containsTypeVariable(typeName);
    }

    private ExecutableElement findMatchingTargetMethod(ExecutableElement source, TypeElement target) {
        // This mirrors the logic in TargetMethodSignatureRule but returns the element
        for (javax.lang.model.element.Element enclosed : target.getEnclosedElements()) {
            if (enclosed instanceof ExecutableElement targetMethod) {
                if (targetMethod.getSimpleName().equals(source.getSimpleName()) &&
                    targetMethod.getParameters().size() == source.getParameters().size()) {
                    // In a more complex scenario, we'd check types here too, 
                    // but we assume the validation phase already filtered this.
                    return targetMethod;
                }
            }
        }
        throw new IllegalStateException("Matching target method not found for: " + source);
    }
}
