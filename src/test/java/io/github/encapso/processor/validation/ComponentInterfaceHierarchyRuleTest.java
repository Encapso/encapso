package io.github.encapso.processor.validation;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.encapso.processor.ComponentProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

class ComponentInterfaceHierarchyRuleTest {

        @Test
        @DisplayName("Should fail compilation if @Component interface extends another interface")
        void shouldFailIfComponentExtendsInterface() {
                JavaFileObject parentInterface = JavaFileObjects.forSourceLines(
                                "test.Parent",
                                "package test;",
                                "public interface Parent {",
                                "    void parentMethod();",
                                "}");

                JavaFileObject componentInterface = JavaFileObjects.forSourceLines(
                                "test.MyComponent",
                                "package test;",
                                "import io.github.encapso.Component;",
                                "@Component",
                                "public interface MyComponent extends Parent {",
                                "    void childMethod();",
                                "}");

                Compilation compilation = javac()
                                .withProcessors(new ComponentProcessor())
                                .compile(parentInterface, componentInterface);

                assertThat(compilation).failed();
                assertThat(compilation).hadErrorContaining(
                                "The @Component interface MyComponent cannot extend other interfaces");
        }
}
