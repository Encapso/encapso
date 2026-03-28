package io.github.encapso.integration;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.encapso.processor.ComponentProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

class NullSafetyCompilationIntegrationTest {

    @Test
    @DisplayName("Should successfully compile with @javax.annotation.Nonnull")
    void shouldCompileWithNonnull() {
        verifyCompilationWithAnnotation("javax.annotation.Nonnull");
    }

    @Test
    @DisplayName("Should successfully compile with @org.jetbrains.annotations.NotNull")
    void shouldCompileWithNotNull() {
        JavaFileObject notNull = JavaFileObjects.forSourceLines(
                "org.jetbrains.annotations.NotNull",
                "package org.jetbrains.annotations;",
                "import java.lang.annotation.*;",
                "@Target(ElementType.PARAMETER) @Retention(RetentionPolicy.CLASS)",
                "public @interface NotNull {}");

        verifyCompilationWithAnnotation("org.jetbrains.annotations.NotNull", notNull);
    }

    @Test
    @DisplayName("Should successfully compile with custom @NonNull")
    void shouldCompileWithCustomNonNull() {
        JavaFileObject nonNull = JavaFileObjects.forSourceLines(
                "com.example.NonNull",
                "package com.example;",
                "import java.lang.annotation.*;",
                "@Target(ElementType.PARAMETER) @Retention(RetentionPolicy.CLASS)",
                "public @interface NonNull {}");

        verifyCompilationWithAnnotation("com.example.NonNull", nonNull);
    }

    private void verifyCompilationWithAnnotation(String annotationName, JavaFileObject... extraFiles) {
        JavaFileObject repo = JavaFileObjects.forSourceLines("repo.BookRepository", "package repo; public class BookRepository {}");
        JavaFileObject service = JavaFileObjects.forSourceLines("repo.OrderProcessor",
                "package repo;",
                "import " + annotationName + ";",
                "public class OrderProcessor {",
                "    public OrderProcessor(@" + annotationName.substring(annotationName.lastIndexOf('.') + 1) + " BookRepository repo) {}",
                "    public void process() {}",
                "}");
        JavaFileObject facade = JavaFileObjects.forSourceLines("repo.BookshopFacade",
                "package repo; import io.github.encapso.*;",
                "@Component public interface BookshopFacade { @DelegateTo(OrderProcessor.class) void process(); }");

        JavaFileObject[] allFiles = new JavaFileObject[3 + extraFiles.length];
        allFiles[0] = repo;
        allFiles[1] = service;
        allFiles[2] = facade;
        System.arraycopy(extraFiles, 0, allFiles, 3, extraFiles.length);

        Compilation compilation = javac().withProcessors(new ComponentProcessor()).compile(allFiles);
        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("repo.BookshopFacadeBuilder");
    }
}
