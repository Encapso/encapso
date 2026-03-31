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
    @DisplayName("Should prefer package-private constructor with more parameters over public default in same package")
    void shouldPreferPackagePrivateConstructorInSamePackage() {
        JavaFileObject dependency = JavaFileObjects.forSourceLines("test.Dep", "package test; public class Dep {}");
        JavaFileObject target = JavaFileObjects.forSourceLines(
                "test.Target",
                "package test;",
                "public class Target {",
                "    public Target() {}", // 0 params
                "    Target(Dep d1) {}", // 1 param, package-private (visible)
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
                "    @DelegateTo(Target.class)",
                "    void execute();",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(dependency, target, component);

        assertThat(compilation).succeeded();
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
    @DisplayName("Should prefer visible constructor with most parameters")
    void shouldPreferVisibleConstructorWithMostParameters() {
        JavaFileObject dependency = JavaFileObjects.forSourceLines("test.Dep", "package test; public class Dep {}");
        JavaFileObject target = JavaFileObjects.forSourceLines(
                "test.Target",
                "package test;",
                "public class Target {",
                "    private Target(Dep d1, Dep d2) {}", // ignored
                "    public Target(Dep d1) {}", // selected
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
                "    @DelegateTo(Target.class)",
                "    void execute();",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(dependency, target, component);

        assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Should prioritize @Inject constructor even if it has fewer parameters")
    void shouldPrioritizeInjectConstructor() {
        JavaFileObject dependency = JavaFileObjects.forSourceLines("test.Dep", "package test; public class Dep {}");
        JavaFileObject inject = JavaFileObjects.forSourceLines("jakarta.inject.Inject", "package jakarta.inject; public @interface Inject {}");
        
        JavaFileObject target = JavaFileObjects.forSourceLines(
                "test.Target",
                "package test;",
                "import jakarta.inject.Inject;",
                "public class Target {",
                "    public Target(Dep d1, Dep d2) {}", // more params, but no @Inject
                "    @Inject public Target(Dep d1) {}", // fewer params, but has @Inject
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
                "    @DelegateTo(Target.class)",
                "    void execute();",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(dependency, inject, target, component);

        assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Should fail if multiple @Inject constructors exist")
    void shouldFailOnMultipleInjectConstructors() {
        JavaFileObject inject = JavaFileObjects.forSourceLines("jakarta.inject.Inject", "package jakarta.inject; public @interface Inject {}");
        JavaFileObject target = JavaFileObjects.forSourceLines(
                "test.Target",
                "package test;",
                "import jakarta.inject.Inject;",
                "public class Target {",
                "    @Inject public Target() {}",
                "    @Inject public Target(String s) {}",
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
                .compile(inject, target, component);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Multiple @Inject constructors found");
    }

    @Test
    @DisplayName("Should fail on constructor ambiguity and advise using @Inject")
    void shouldFailOnConstructorAmbiguityAndAdviseInject() {
        JavaFileObject target = JavaFileObjects.forSourceLines(
                "test.Target",
                "package test;",
                "public class Target {",
                "    public Target(String s) {}",
                "    public Target(Integer i) {}",
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
        assertThat(compilation).hadErrorContaining("Ambiguous constructors");
        assertThat(compilation).hadErrorContaining("Please annotate the intended constructor with @Inject");
    }

    @Test
    @DisplayName("Should prioritize @Inject factory method overload")
    void shouldPrioritizeInjectFactoryOverload() {
        JavaFileObject inject = JavaFileObjects.forSourceLines("jakarta.inject.Inject", "package jakarta.inject; public @interface Inject {}");
        JavaFileObject target = JavaFileObjects.forSourceLines(
                "test.Target",
                "package test;",
                "import jakarta.inject.Inject;",
                "public class Target {",
                "    public static Target create(String s) { return new Target(); }",
                "    @Inject public static Target create() { return new Target(); }",
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
                .compile(inject, target, component);

        assertThat(compilation).succeeded();
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

    @Test
    @DisplayName("Should fail if factory method returns wrong type")
    void shouldFailIfFactoryMethodReturnsWrongType() {
        JavaFileObject target = JavaFileObjects.forSourceLines(
                "test.Target",
                "package test;",
                "public class Target {",
                "    public static String create() { return \"wrong\"; }",
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
        assertThat(compilation).hadErrorContaining("must have a 'static' factory method named 'create' that returns Target");
    }

    @Test
    @DisplayName("Should fail if factory method is not visible")
    void shouldFailIfFactoryMethodIsNotVisible() {
        JavaFileObject target = JavaFileObjects.forSourceLines(
                "other.Target",
                "package other;",
                "public class Target {",
                "    static Target create() { return new Target(); }", // package-private
                "}"
        );

        JavaFileObject component = JavaFileObjects.forSourceLines(
                "test.MyComponent",
                "package test;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "import other.Target;",
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
        assertThat(compilation).hadErrorContaining("is visible to the Component");
    }

    @Test
    @DisplayName("Should fail if target class is abstract and no factory method")
    void shouldFailIfTargetIsAbstractAndNoFactory() {
        JavaFileObject target = JavaFileObjects.forSourceLines(
                "test.Target",
                "package test;",
                "public abstract class Target {",
                "    public Target() {}",
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
        assertThat(compilation).hadErrorContaining("Target class Target is abstract or an interface and cannot be instantiated");
    }

    @Test
    @DisplayName("Should succeed if target class is abstract but factory method is used")
    void shouldSucceedIfTargetIsAbstractButHasFactory() {
        JavaFileObject target = JavaFileObjects.forSourceLines(
                "test.Target",
                "package test;",
                "public abstract class Target {",
                "    public static Target create() { return null; }",
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

    @Test
    @DisplayName("Should fail if target is an interface and no factory method")
    void shouldFailIfTargetIsInterfaceAndNoFactory() {
        JavaFileObject target = JavaFileObjects.forSourceLines(
                "test.Target",
                "package test;",
                "public interface Target {",
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
        assertThat(compilation).hadErrorContaining("Target class Target is abstract or an interface and cannot be instantiated");
    }

    @Test
    @DisplayName("Should succeed if factory method returns a subtype")
    void shouldSucceedIfFactoryMethodReturnsSubtype() {
        JavaFileObject target = JavaFileObjects.forSourceLines(
                "test.Target",
                "package test;",
                "public class Target {",
                "    public static Child create() { return new Child(); }",
                "    public void execute() {}",
                "}"
        );

        JavaFileObject child = JavaFileObjects.forSourceLines(
                "test.Child",
                "package test;",
                "public class Child extends Target {}"
        );

        JavaFileObject component = JavaFileObjects.forSourceLines(
                "test.MyComponent",
                "package test;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "import test.Target;",
                "@Component",
                "public interface MyComponent {",
                "    @DelegateTo(value = Target.class, factoryMethod = \"create\")",
                "    void execute();",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(target, child, component);

        assertThat(compilation).succeeded();
    }
}
