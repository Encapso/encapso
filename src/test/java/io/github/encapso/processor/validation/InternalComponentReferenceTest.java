package io.github.encapso.processor.validation;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.encapso.processor.ComponentProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

class InternalComponentReferenceTest {

    @Test
    @DisplayName("Should fail compilation if an internal class refers to its own @Component interface via constructor")
    void shouldFailWhenInternalClassRefersToItsOwnComponent() {
        JavaFileObject internalClass = JavaFileObjects.forSourceLines(
                "test.InternalDelegate",
                "package test;",
                "",
                "public class InternalDelegate {",
                "    private final Iface component;",
                "    public InternalDelegate(Iface component) {",
                "        this.component = component;",
                "    }",
                "    public void doSomething() {}",
                "}"
        );

        JavaFileObject iface = JavaFileObjects.forSourceLines(
                "test.Iface",
                "package test;",
                "",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "",
                "@Component",
                "public interface Iface {",
                "    @DelegateTo(InternalDelegate.class)",
                "    void doSomething();",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(internalClass, iface);

        assertThat(compilation).failed();
        assertThat(compilation)
                .hadErrorContaining("Internal class 'InternalDelegate' cannot refer to its own component interface 'Iface'.")
                .inFile(internalClass);
    }

    @Test
    @DisplayName("Should fail compilation if an internal class refers to its own @Component interface via field")
    void shouldFailWhenInternalClassRefersToItsOwnComponentViaField() {
        JavaFileObject internalClass = JavaFileObjects.forSourceLines(
                "test.InternalDelegate",
                "package test;",
                "",
                "public class InternalDelegate {",
                "    public Iface component;",
                "    public void doSomething() {}",
                "}"
        );

        JavaFileObject iface = JavaFileObjects.forSourceLines(
                "test.Iface",
                "package test;",
                "",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "",
                "@Component",
                "public interface Iface {",
                "    @DelegateTo(InternalDelegate.class)",
                "    void doSomething();",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(internalClass, iface);

        assertThat(compilation).failed();
        assertThat(compilation)
                .hadErrorContaining("Internal class 'InternalDelegate' cannot refer to its own component interface 'Iface'.")
                .inFile(internalClass);
    }

    @Test
    @DisplayName("Should fail compilation if an internal class refers to its own Builder")
    void shouldFailWhenInternalClassRefersToItsOwnBuilder() {
        JavaFileObject internalClass = JavaFileObjects.forSourceLines(
                "test.InternalDelegate",
                "package test;",
                "",
                "public class InternalDelegate {",
                "    public void useBuilder(IfaceBuilder builder) {}",
                "    public void doSomething() {}",
                "}"
        );

        JavaFileObject iface = JavaFileObjects.forSourceLines(
                "test.Iface",
                "package test;",
                "",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "",
                "@Component",
                "public interface Iface {",
                "    @DelegateTo(InternalDelegate.class)",
                "    void doSomething();",
                "}"
        );

        // We also need a dummy Builder class in the same package (mocking the generated one)
        JavaFileObject builderClass = JavaFileObjects.forSourceLines(
                "test.IfaceBuilder",
                "package test;",
                "public class IfaceBuilder {}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(internalClass, iface, builderClass);

        assertThat(compilation).failed();
        assertThat(compilation)
                .hadErrorContaining("Internal class 'InternalDelegate' cannot refer to its own component interface 'IfaceBuilder'.")
                .inFile(internalClass);
    }
}
