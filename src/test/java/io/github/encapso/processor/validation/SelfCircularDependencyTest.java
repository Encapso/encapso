package io.github.encapso.processor.validation;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.encapso.processor.ComponentProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

class SelfCircularDependencyTest {

    @Test
    @DisplayName("Should fail when a target class depends on itself in its constructor")
    void shouldFailOnSelfDependency() {
        JavaFileObject serviceA = JavaFileObjects.forSourceLines(
                "billing.ServiceA",
                "package billing;",
                "public class ServiceA {",
                "    public ServiceA(ServiceA a) {}",
                "    public void doWork() {}",
                "}"
        );

        JavaFileObject facade = JavaFileObjects.forSourceLines(
                "billing.BillingFacade",
                "package billing;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "@Component",
                "public interface BillingFacade {",
                "    @DelegateTo(ServiceA.class)",
                "    void doWork();",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(serviceA, facade);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Self-Circular dependency detected");
        assertThat(compilation).hadErrorContaining("ServiceA' depends on itself via its constructor");
    }

    @Test
    @DisplayName("Should fail when a target class depends on itself in its static factory method")
    void shouldFailOnSelfDependencyInFactory() {
        JavaFileObject serviceA = JavaFileObjects.forSourceLines(
                "billing.ServiceA",
                "package billing;",
                "public class ServiceA {",
                "    private ServiceA() {}",
                "    public static ServiceA create(ServiceA a) { return new ServiceA(); }",
                "    public void doWork() {}",
                "}"
        );

        JavaFileObject facade = JavaFileObjects.forSourceLines(
                "billing.BillingFacade",
                "package billing;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "@Component",
                "public interface BillingFacade {",
                "    @DelegateTo(value = ServiceA.class, factoryMethod = \"create\")",
                "    void doWork();",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(serviceA, facade);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Self-Circular dependency detected");
    }
}
