package io.github.encapso.processor.validation;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.encapso.processor.ComponentProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

class ComponentInterfaceRuleTest {

    @Test
    @DisplayName("Should succeed when @Component is placed on an interface with at least one @DelegateTo method")
    void shouldSucceedOnInterfaceWithDelegate() {
        JavaFileObject target = JavaFileObjects.forSourceLines(
                "pkg.MyService",
                "package pkg;",
                "public class MyService {",
                "    public void doSomething() {}",
                "}"
        );
        JavaFileObject iface = JavaFileObjects.forSourceLines(
                "pkg.MyComponent",
                "package pkg;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "@Component",
                "public interface MyComponent {",
                "    @DelegateTo(MyService.class)",
                "    void doSomething();",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(target, iface);

        assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Should fail when @Component has no @DelegateTo methods")
    void shouldFailOnEmptyInterface() {
        JavaFileObject iface = JavaFileObjects.forSourceLines(
                "pkg.MyComponent",
                "package pkg;",
                "import io.github.encapso.Component;",
                "@Component",
                "public interface MyComponent {}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(iface);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@Component must have at least one method annotated with @DelegateTo");
    }

    @Test
    @DisplayName("Should fail when @Component is placed on a class")
    void shouldFailOnClass() {
        JavaFileObject clazz = JavaFileObjects.forSourceLines(
                "pkg.MyComponent",
                "package pkg;",
                "import io.github.encapso.Component;",
                "@Component",
                "public class MyComponent {}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(clazz);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@Component can only be placed on interfaces. Found on class: MyComponent");
    }

    @Test
    @DisplayName("Should fail when @Component is placed on an abstract class")
    void shouldFailOnAbstractClass() {
        JavaFileObject clazz = JavaFileObjects.forSourceLines(
                "pkg.MyComponent",
                "package pkg;",
                "import io.github.encapso.Component;",
                "@Component",
                "public abstract class MyComponent {}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(clazz);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@Component can only be placed on interfaces. Found on class: MyComponent");
    }

    @Test
    @DisplayName("Should fail when @Component is placed on an enum")
    void shouldFailOnEnum() {
        JavaFileObject enumType = JavaFileObjects.forSourceLines(
                "pkg.MyComponent",
                "package pkg;",
                "import io.github.encapso.Component;",
                "@Component",
                "public enum MyComponent { INSTANCE }"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(enumType);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@Component can only be placed on interfaces. Found on enum: MyComponent");
    }

    @Test
    @DisplayName("Should fail when @Component is placed on a record")
    void shouldFailOnRecord() {
        JavaFileObject recordType = JavaFileObjects.forSourceLines(
                "pkg.MyComponent",
                "package pkg;",
                "import io.github.encapso.Component;",
                "@Component",
                "public record MyComponent() {}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(recordType);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@Component can only be placed on interfaces. Found on record: MyComponent");
    }
}
