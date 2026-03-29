package io.github.encapso.processor.validation;

import com.google.testing.compile.JavaFileObjects;
import io.github.encapso.processor.ComponentProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.Compiler.javac;
import static com.google.testing.compile.CompilationSubject.assertThat;

class SingleComponentPerPackageRuleTest {

    @Test
    @DisplayName("Should succeed if package has only one component")
    void shouldSucceedWithOneComponent() {
        JavaFileObject component = JavaFileObjects.forSourceLines(
                "io.github.encapso.ComponentA",
                "package io.github.encapso;",
                "import io.github.encapso.Component;",
                "@Component",
                "public interface ComponentA {}"
        );

        var compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(component);

        assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Should fail if package has multiple components")
    void shouldFailWithMultipleComponents() {
        JavaFileObject componentA = JavaFileObjects.forSourceLines(
                "io.github.encapso.ComponentA",
                "package io.github.encapso;",
                "import io.github.encapso.Component;",
                "@Component",
                "public interface ComponentA {}"
        );
        JavaFileObject componentB = JavaFileObjects.forSourceLines(
                "io.github.encapso.ComponentB",
                "package io.github.encapso;",
                "import io.github.encapso.Component;",
                "@Component",
                "public interface ComponentB {}"
        );

        var compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(componentA, componentB);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Package 'io.github.encapso' contains multiple @Component interfaces: [ComponentA, ComponentB]");
    }

    @Test
    @DisplayName("Should succeed with components in nested packages")
    void shouldSucceedWithNestedPackages() {
        JavaFileObject parent = JavaFileObjects.forSourceLines(
                "io.github.encapso.Parent",
                "package io.github.encapso;",
                "import io.github.encapso.Component;",
                "@Component",
                "public interface Parent {}"
        );
        JavaFileObject child = JavaFileObjects.forSourceLines(
                "io.github.encapso.sub.Child",
                "package io.github.encapso.sub;",
                "import io.github.encapso.Component;",
                "@Component",
                "public interface Child {}"
        );

        var compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(parent, child);

        assertThat(compilation).succeeded();
    }
}
