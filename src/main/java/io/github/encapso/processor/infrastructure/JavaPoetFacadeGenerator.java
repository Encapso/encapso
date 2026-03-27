package io.github.encapso.processor.infrastructure;

import com.squareup.javapoet.*;
import io.github.encapso.processor.ComponentProcessor;
import io.github.encapso.processor.domain.DependencyGraph;
import io.github.encapso.processor.domain.FacadeGenerator;

import javax.annotation.processing.Generated;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.Map;

public class JavaPoetFacadeGenerator implements FacadeGenerator {

    @Override
    public void generateFacade(TypeElement interfaceElement,
                               Map<ExecutableElement, TypeElement> delegateMapping,
                               DependencyGraph graph,
                               ProcessingEnvironment processingEnv) {
        String packageName = processingEnv.getElementUtils()
                .getPackageOf(interfaceElement).getQualifiedName().toString();
        String generatedClassName = interfaceElement.getSimpleName() + "Impl";

        TypeSpec classSpec = buildClass(interfaceElement, generatedClassName, delegateMapping, graph);

        try {
            JavaFile.builder(packageName, classSpec).indent("    ").build()
                    .writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate Facade: " + e.getMessage());
        }
    }

    private TypeSpec buildClass(TypeElement interfaceElement, String generatedClassName,
                                Map<ExecutableElement, TypeElement> delegateMapping,
                                DependencyGraph graph) {
        // Package-private class — no Modifier.PUBLIC
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(generatedClassName)
                .addModifiers(Modifier.FINAL)
                .addSuperinterface(TypeName.get(interfaceElement.asType()))
                .addAnnotation(AnnotationSpec.builder(Generated.class)
                        .addMember("value", "$S", ComponentProcessor.class.getCanonicalName())
                        .build());

        Map<TypeElement, String> tcNames = graph.tcInstanceNames();

        // Fields — one per unique TC, named from DependencyGraph (package-private)
        for (Map.Entry<TypeElement, String> entry : tcNames.entrySet()) {
            classBuilder.addField(
                    FieldSpec.builder(TypeName.get(entry.getKey().asType()), entry.getValue())
                            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                            .build());
        }

        // Package-private constructor — takes TCs in graph order
        MethodSpec.Builder ctor = MethodSpec.constructorBuilder(); // no Modifier.PUBLIC
        for (Map.Entry<TypeElement, String> entry : tcNames.entrySet()) {
            ctor.addParameter(TypeName.get(entry.getKey().asType()), entry.getValue());
            ctor.addStatement("this.$N = $N", entry.getValue(), entry.getValue());
        }
        classBuilder.addMethod(ctor.build());

        // Delegate methods
        for (Map.Entry<ExecutableElement, TypeElement> entry : delegateMapping.entrySet()) {
            ExecutableElement method = entry.getKey();
            String fieldName = tcNames.get(entry.getValue());

            MethodSpec.Builder methodBuilder = MethodSpec.overriding(method);
            StringBuilder args = new StringBuilder();
            for (int i = 0; i < method.getParameters().size(); i++) {
                if (i > 0) args.append(", ");
                args.append(method.getParameters().get(i).getSimpleName());
            }

            if (method.getReturnType().getKind().name().equals("VOID")) {
                methodBuilder.addStatement("this.$N.$N($L)", fieldName, method.getSimpleName(), args);
            } else {
                methodBuilder.addStatement("return this.$N.$N($L)", fieldName, method.getSimpleName(), args);
            }
            classBuilder.addMethod(methodBuilder.build());
        }

        return classBuilder.build();
    }
}
