package io.github.encapso.processor.validation;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.encapso.processor.ComponentProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

class TargetClassInstantiatorRuleTest {

    @Test
    @DisplayName("Should fail if factory method is not static")
    void shouldFailIfFactoryMethodIsNotStatic() {
        JavaFileObject target = JavaFileObjects.forSourceLines(
                "test.Target",
                "package test;",
                "public class Target {",
                "    public Target create() { return new Target(); }",
                "}"
        );

        JavaFileObject component = JavaFileObjects.forSourceLines(
                "test.MyComponent",
                "package test;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "@Component",
                "public interface MyComponent {",
                "    @DelegateTo(value = Target.class, factoryMethod = \"create\")",
                "    void execute();",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(target, component);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("must have a 'static' factory method named 'create'");
    }

    @Test
    @DisplayName("Should fail if factory method returns wrong type")
    void shouldFailIfFactoryMethodReturnsWrongType() {
        JavaFileObject target = JavaFileObjects.forSourceLines(
                "test.Target",
                "package test;",
                "public class Target {",
                "    public static String create() { return \"\"; }",
                "}"
        );

        JavaFileObject component = JavaFileObjects.forSourceLines(
                "test.MyComponent",
                "package test;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "@Component",
                "public interface MyComponent {",
                "    @DelegateTo(value = Target.class, factoryMethod = \"create\")",
                "    void execute();",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(target, component);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("returns Target and is visible");
    }

    @Test
    @DisplayName("Should fail if no visible constructor exists and no factory method")
    void shouldFailIfNoVisibleConstructor() {
        JavaFileObject target = JavaFileObjects.forSourceLines(
                "test.Target",
                "package test;",
                "public class Target {",
                "    private Target() {}",
                "}"
        );

        JavaFileObject component = JavaFileObjects.forSourceLines(
                "test.MyComponent",
                "package test;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "@Component",
                "public interface MyComponent {",
                "    @DelegateTo(Target.class)",
                "    void execute();",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(target, component);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("must have a visible constructor");
    }

    @Test
    @DisplayName("Should succeed with valid static factory method")
    void shouldSucceedWithValidFactory() {
        JavaFileObject target = JavaFileObjects.forSourceLines(
                "test.Target",
                "package test;",
                "public class Target {",
                "    private Target() {}",
                "    public static Target create() { return new Target(); }",
                "    public void execute() {}",
                "}"
        );

        JavaFileObject component = JavaFileObjects.forSourceLines(
                "test.MyComponent",
                "package test;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "@Component",
                "public interface MyComponent {",
                "    @DelegateTo(value = Target.class, factoryMethod = \"create\")",
                "    void execute();",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(target, component);

        assertThat(compilation).succeeded();
    }
}
