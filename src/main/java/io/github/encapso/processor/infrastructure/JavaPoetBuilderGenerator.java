package io.github.encapso.processor.infrastructure;

import com.squareup.javapoet.*;
import io.github.encapso.processor.ComponentProcessor;
import io.github.encapso.processor.domain.BuilderGenerator;
import io.github.encapso.processor.domain.DependencyGraph;
import io.github.encapso.processor.domain.DependencyGraph.ExternalDependency;
import io.github.encapso.processor.domain.DependencyGraph.InstantiationStep;

import javax.annotation.processing.Generated;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;


public class JavaPoetBuilderGenerator implements BuilderGenerator {

    @Override
    public void generateBuilder(TypeElement interfaceElement, DependencyGraph graph,
                                ProcessingEnvironment processingEnv) {
        String packageName = processingEnv.getElementUtils()
                .getPackageOf(interfaceElement).getQualifiedName().toString();
        String interfaceName = interfaceElement.getSimpleName().toString();
        String builderName = interfaceName + "Builder";
        String implName = interfaceName + "Impl";

        TypeSpec builderClass = buildClass(interfaceElement, builderName, implName, graph, packageName);

        try {
            JavaFile.builder(packageName, builderClass).indent("    ").build()
                    .writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate ComponentBuilder: " + e.getMessage());
        }
    }

    private TypeSpec buildClass(TypeElement interfaceElement, String builderName, String implName,
                                DependencyGraph graph, String packageName) {
        ClassName builderType = ClassName.get(packageName, builderName);
        TypeName interfaceType = TypeName.get(interfaceElement.asType());

        TypeSpec.Builder cls = TypeSpec.classBuilder(builderName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(AnnotationSpec.builder(Generated.class)
                        .addMember("value", "$S", ComponentProcessor.class.getCanonicalName())
                        .build())
                .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
                .addMethod(MethodSpec.methodBuilder("newBuilder")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(builderType)
                        .addStatement("return new $T()", builderType)
                        .build());

        // One private field + fluent setter per external dependency
        for (ExternalDependency dep : graph.externalDependencies()) {
            TypeName depType = TypeName.get(dep.type().asType());
            cls.addField(FieldSpec.builder(depType, dep.paramName(), Modifier.PRIVATE).build());
            cls.addMethod(MethodSpec.methodBuilder(dep.paramName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderType)
                    .addParameter(depType, dep.paramName())
                    .addStatement("this.$N = $N", dep.paramName(), dep.paramName())
                    .addStatement("return this")
                    .build());
        }

        // build() — topologically ordered instantiation, returns the interface type
        MethodSpec.Builder build = MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(interfaceType);

        for (InstantiationStep step : graph.instantiationSteps()) {
            TypeName stepType = TypeName.get(step.type().asType());
            String args = String.join(", ", step.constructorArgs());
            build.addStatement("$T $L = new $T($L)", stepType, step.instanceName(), stepType, args);
        }

        String tcArgs = String.join(", ", graph.tcInstanceNames().values());
        build.addStatement("return new $L($L)", implName, tcArgs);
        cls.addMethod(build.build());

        return cls.build();
    }
}
