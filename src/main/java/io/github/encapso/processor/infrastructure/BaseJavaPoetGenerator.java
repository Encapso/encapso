package io.github.encapso.processor.infrastructure;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import io.github.encapso.processor.ComponentProcessor;

import javax.annotation.processing.Generated;
import java.io.IOException;

/**
 * Base class for JavaPoet-based source code generators.
 * Provides common utilities for adding @Generated annotations and writing files.
 */
public abstract class BaseJavaPoetGenerator {

    protected void writeToFile(String packageName, TypeSpec typeSpec, GeneratorContext context) {
        TypeSpec annotatedSpec = typeSpec.toBuilder()
                .addAnnotation(AnnotationSpec.builder(Generated.class)
                        .addMember("value", "$S", ComponentProcessor.class.getName())
                        .build())
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, annotatedSpec)
                .skipJavaLangImports(true)
                .build();

        try {
            javaFile.writeTo(context.filer());
        } catch (IOException e) {
            context.messager().printMessage(javax.tools.Diagnostic.Kind.ERROR,
                    "Failed to write generated file: " + e.getMessage());
        }
    }
}
