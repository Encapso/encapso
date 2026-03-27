package io.github.encapso.processor.infrastructure;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import io.github.encapso.processor.ComponentProcessor;
import io.github.encapso.processor.domain.FacadeGenerator;

import javax.annotation.processing.Generated;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class JavaPoetFacadeGenerator implements FacadeGenerator {

    @Override
    public void generateFacade(TypeElement interfaceElement, Map<ExecutableElement, TypeElement> delegateMapping, ProcessingEnvironment processingEnv) {
        String packageName = processingEnv.getElementUtils().getPackageOf(interfaceElement).getQualifiedName().toString();
        String interfaceName = interfaceElement.getSimpleName().toString();
        String generatedClassName = interfaceName + "Impl";

        Map<TypeElement, String> targetClassToFieldName = buildFieldNameMapping(delegateMapping);
        TypeSpec classSpec = buildComponentClass(interfaceElement, generatedClassName, delegateMapping, targetClassToFieldName);

        writeJavaFile(packageName, classSpec, processingEnv);
    }

    private Map<TypeElement, String> buildFieldNameMapping(Map<ExecutableElement, TypeElement> delegateMapping) {
        Map<TypeElement, String> targetClassToFieldName = new LinkedHashMap<>();
        
        for (TypeElement targetClass : delegateMapping.values()) {
            if (!targetClassToFieldName.containsKey(targetClass)) {
                String baseName = targetClass.getSimpleName().toString();
                baseName = Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1);

                String fieldName = baseName;
                int counter = 1;
                while (targetClassToFieldName.containsValue(fieldName)) {
                    counter++;
                    fieldName = baseName + counter;
                }

                targetClassToFieldName.put(targetClass, fieldName);
            }
        }
        return targetClassToFieldName;
    }

    private TypeSpec buildComponentClass(TypeElement interfaceElement, String generatedClassName, 
                                                Map<ExecutableElement, TypeElement> delegateMapping, 
                                                Map<TypeElement, String> targetClassToFieldName) {
                                                    
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(generatedClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(TypeName.get(interfaceElement.asType()))
                .addAnnotation(AnnotationSpec.builder(Generated.class)
                        .addMember("value", "$S", ComponentProcessor.class.getCanonicalName())
                        .build());

        addFields(classBuilder, targetClassToFieldName);
        addConstructor(classBuilder, targetClassToFieldName);
        addDelegateMethods(classBuilder, delegateMapping, targetClassToFieldName);

        return classBuilder.build();
    }

    private void addFields(TypeSpec.Builder classBuilder, Map<TypeElement, String> targetClassToFieldName) {
        for (Map.Entry<TypeElement, String> entry : targetClassToFieldName.entrySet()) {
            TypeElement targetClass = entry.getKey();
            String fieldName = entry.getValue();

            FieldSpec fieldSpec = FieldSpec.builder(TypeName.get(targetClass.asType()), fieldName)
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                    .build();
            classBuilder.addField(fieldSpec);
        }
    }

    private void addConstructor(TypeSpec.Builder classBuilder, Map<TypeElement, String> targetClassToFieldName) {
        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);

        for (Map.Entry<TypeElement, String> entry : targetClassToFieldName.entrySet()) {
            TypeElement targetClass = entry.getKey();
            String fieldName = entry.getValue();

            constructorBuilder.addParameter(TypeName.get(targetClass.asType()), fieldName);
            constructorBuilder.addStatement("this.$N = $N", fieldName, fieldName);
        }
        
        classBuilder.addMethod(constructorBuilder.build());
    }

    private void addDelegateMethods(TypeSpec.Builder classBuilder, 
                                           Map<ExecutableElement, TypeElement> delegateMapping, 
                                           Map<TypeElement, String> targetClassToFieldName) {
                                               
        for (Map.Entry<ExecutableElement, TypeElement> entry : delegateMapping.entrySet()) {
            ExecutableElement methodElement = entry.getKey();
            TypeElement targetClass = entry.getValue();
            String fieldName = targetClassToFieldName.get(targetClass);

            MethodSpec.Builder methodBuilder = MethodSpec.overriding(methodElement);

            StringBuilder argsBuilder = new StringBuilder();
            for (int i = 0; i < methodElement.getParameters().size(); i++) {
                if (i > 0) argsBuilder.append(", ");
                argsBuilder.append(methodElement.getParameters().get(i).getSimpleName());
            }

            if (methodElement.getReturnType().getKind().name().equals("VOID")) {
                methodBuilder.addStatement("this.$N.$N($L)", fieldName, methodElement.getSimpleName(), argsBuilder.toString());
            } else {
                methodBuilder.addStatement("return this.$N.$N($L)", fieldName, methodElement.getSimpleName(), argsBuilder.toString());
            }

            classBuilder.addMethod(methodBuilder.build());
        }
    }

    private void writeJavaFile(String packageName, TypeSpec classSpec, ProcessingEnvironment processingEnv) {
        JavaFile javaFile = JavaFile.builder(packageName, classSpec)
                .indent("    ")
                .build();

        try {
            javaFile.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to natively generate Component Facade: " + e.getMessage());
        }
    }
}
