package io.github.encapso.processor.integration;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.encapso.processor.ComponentProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

class BuilderOverloadTest {

    @Test
    @DisplayName("Should generate builder with overloads for same-name different-type dependencies")
    void shouldGenerateOverloadsForSameName() {
        JavaFileObject target1 = JavaFileObjects.forSourceLines(
                "test.Target1",
                "package test;",
                "public class Target1 {",
                "    public Target1(String val) {}",
                "    public void execute1() {}",
                "}"
        );

        JavaFileObject target2 = JavaFileObjects.forSourceLines(
                "test.Target2",
                "package test;",
                "public class Target2 {",
                "    public Target2(Integer val) {}",
                "    public void execute2() {}",
                "}"
        );

        JavaFileObject component = JavaFileObjects.forSourceLines(
                "test.MyComponent",
                "package test;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "@Component",
                "public interface MyComponent {",
                "    @DelegateTo(Target1.class)",
                "    void execute1();",
                "    @DelegateTo(Target2.class)",
                "    void execute2();",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(target1, target2, component);

        // Currently, this should technically fail with unique field name collisions in generated code.
        // We want it to succeed after we fix the generator.
        assertThat(compilation).succeeded();
    }
}
