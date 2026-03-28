package io.github.encapso.processor.infrastructure;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.ParameterizedTypeName;
import io.github.encapso.processor.ComponentProcessor;
import io.github.encapso.processor.domain.BuilderGenerator;
import io.github.encapso.processor.domain.DependencyGraph;
import io.github.encapso.processor.domain.DependencyGraph.ExternalDependency;
import io.github.encapso.processor.domain.DependencyGraph.InstantiationStep;

import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.Map;



public class JavaPoetBuilderGenerator implements BuilderGenerator {

    @Override
    public void generateBuilder(TypeElement interfaceElement, GeneratorContext context) {
        String packageName = context.getPackageName(interfaceElement);
        String interfaceName = interfaceElement.getSimpleName().toString();
        String builderName = interfaceName + "Builder";
        String implName = interfaceName + "Impl";

        TypeSpec builderClass = buildClass(interfaceElement, builderName, implName, context, packageName);

        try {
            JavaFile javaFile = JavaFile.builder(packageName, builderClass).indent("    ").build();
            context.messager().printMessage(Diagnostic.Kind.NOTE, "GENERATED BUILDER:\n" + javaFile.toString());
            javaFile.writeTo(context.filer());
        } catch (IOException e) {
            context.messager().printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate ComponentBuilder: " + e.getMessage());
        }
    }

    private TypeSpec buildClass(TypeElement interfaceElement, String builderName, String implName,
                                GeneratorContext context, String packageName) {
        DependencyGraph graph = context.graph();
        // Handle generic type variables from the interface - preserve bounds for the class declaration
        java.util.List<TypeVariableName> typeVariables = interfaceElement.getTypeParameters().stream()
                .map(TypeVariableName::get)
                .toList();

        // Mapping for unification - use name-only for lookups, but we'll use bounded versions where appropriate
        Map<String, TypeVariableName> typeVarMapping = new java.util.HashMap<>();
        for (TypeVariableName tv : typeVariables) {
            typeVarMapping.put(tv.name, tv);
        }

        // The builder itself needs the same type variables (with bounds)
        TypeName interfaceType = unifyTypes(interfaceElement.asType(), typeVarMapping);
        
        // Use name-only versions for references within the class to avoid duplicate bounds in signatures
        java.util.List<TypeVariableName> typeVarNamesOnly = typeVariables.stream()
                .map(tv -> TypeVariableName.get(tv.name))
                .toList();
        
        TypeName builderType = typeVariables.isEmpty()
                ? ClassName.get(packageName, builderName)
                : ParameterizedTypeName.get(ClassName.get(packageName, builderName), typeVarNamesOnly.toArray(new TypeName[0]));

        TypeSpec.Builder cls = TypeSpec.classBuilder(builderName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addTypeVariables(typeVariables)
                .addAnnotation(AnnotationSpec.builder(Generated.class)
                        .addMember("value", "$S", ComponentProcessor.class.getCanonicalName())
                        .build())
                .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
                .addMethod(MethodSpec.methodBuilder("newBuilder")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addTypeVariables(typeVariables)
                        .returns(builderType)
                        .addStatement("return new $T$L()", ClassName.get(packageName, builderName), typeVariables.isEmpty() ? "" : "<>")
                        .build());

        // One private field (raw) + fluent setter (generic) per external dependency
        for (ExternalDependency dep : graph.externalDependencies()) {
            TypeName depTypeGeneric = unifyTypes(dep.type(), typeVarMapping);
            
            // Strictly enforce raw type for the internal field to avoid all scope issues
            TypeElement te = JavaPoetUtils.toTypeElement(dep.type());
            ClassName depTypeRaw = ClassName.get(te);
            
            cls.addField(FieldSpec.builder(depTypeRaw, dep.paramName(), Modifier.PRIVATE).build());
            MethodSpec.Builder setter = MethodSpec.methodBuilder(dep.paramName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderType)
                    .addParameter(depTypeGeneric, dep.paramName());

            if (dep.required()) {
                setter.addStatement("this.$N = $T.requireNonNull($N, $S)",
                        dep.paramName(), java.util.Objects.class, dep.paramName(),
                        "'" + dep.paramName() + "' must not be null");
            } else {
                setter.addStatement("this.$N = $N", dep.paramName(), dep.paramName());
            }

            cls.addMethod(setter.addStatement("return this").build());
        }

        // build() — topologically ordered instantiation, returns the interface type
        MethodSpec.Builder build = MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(interfaceType);

        for (ExternalDependency dep : graph.externalDependencies()) {
            if (dep.required()) {
                build.addStatement("$T.requireNonNull($N, $S)",
                        java.util.Objects.class, dep.paramName(),
                        "Required dependency '" + dep.paramName() + "' was not provided to the builder");
            }
        }

        for (InstantiationStep step : graph.instantiationSteps()) {
            // Use raw types for internals to avoid unresolvable T scope issues in the builder's local variables
            ClassName stepType = ClassName.get(step.type());
            String args = String.join(", ", step.constructorArgs());
            build.addStatement("$T $L = new $T($L)", stepType, step.instanceName(), stepType, args);
        }

        String tcArgs = String.join(", ", graph.tcInstanceNames().values());
        build.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                .addMember("value", "$S", "unchecked")
                .build());
        build.addStatement("return new $L$L($L)", implName, typeVariables.isEmpty() ? "" : "<>", tcArgs);
        cls.addMethod(build.build());

        return cls.build();
    }


    private TypeName unifyTypes(javax.lang.model.type.TypeMirror mirror, Map<String, TypeVariableName> mapping) {
        TypeName typeName = TypeName.get(mirror);
        return JavaPoetUtils.replaceTypeVariablesWithNameOnly(typeName);
    }
}
