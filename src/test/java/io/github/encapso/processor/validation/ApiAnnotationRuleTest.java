package io.github.encapso.processor.validation;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.encapso.processor.ComponentProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

class ApiAnnotationRuleTest {

    @Test
    @DisplayName("Should fail if @Api is used on a non-public class")
    void shouldFailIfApiTypeIsNotPublic() {
        JavaFileObject type = JavaFileObjects.forSourceLines(
                "billing.Internal",
                "package billing;",
                "import io.github.encapso.Api;",
                "@Api",
                "class Internal {}"
        );

        JavaFileObject component = JavaFileObjects.forSourceLines(
                "billing.BillingFacade",
                "package billing;",
                "import io.github.encapso.Component;",
                "@Component",
                "public interface BillingFacade {}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(type, component);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@Api annotation can only be used on public types");
    }

    @Test
    @DisplayName("Should fail if @Api is used outside a @Component package tree")
    void shouldFailIfApiTypeIsOrphaned() {
        JavaFileObject type = JavaFileObjects.forSourceLines(
                "outside.PublicType",
                "package outside;",
                "import io.github.encapso.Api;",
                "@Api",
                "public class PublicType {}"
        );

        JavaFileObject component = JavaFileObjects.forSourceLines(
                "billing.BillingFacade",
                "package billing;",
                "import io.github.encapso.Component;",
                "@Component",
                "public interface BillingFacade {}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(type, component);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("must be located within a package");
    }

    @Test
    @DisplayName("Should succeed if @Api type is public and inside component tree")
    void shouldSucceedWithCorrectApiUsage() {
        JavaFileObject type = JavaFileObjects.forSourceLines(
                "billing.PublicType",
                "package billing;",
                "import io.github.encapso.Api;",
                "@Api",
                "public class PublicType {",
                "    public void doSomething() {}",
                "}"
        );

        JavaFileObject component = JavaFileObjects.forSourceLines(
                "billing.BillingFacade",
                "package billing;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "@Component",
                "public interface BillingFacade {",
                "    @DelegateTo(PublicType.class) void doSomething();",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(type, component);

        assertThat(compilation).succeeded();
    }
}
