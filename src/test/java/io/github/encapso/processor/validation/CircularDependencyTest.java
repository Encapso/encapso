package io.github.encapso.processor.validation;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.encapso.processor.ComponentProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

class CircularDependencyTest {

    @Test
    @DisplayName("Should fail when a circular dependency exists between internal components (A -> B -> A)")
    void shouldFailOnCircularDependency() {
        JavaFileObject serviceA = JavaFileObjects.forSourceLines(
                "billing.ServiceA",
                "package billing;",
                "public class ServiceA {",
                "    public ServiceA(ServiceB b) {}",
                "    public void doWork() {}",
                "}"
        );

        JavaFileObject serviceB = JavaFileObjects.forSourceLines(
                "billing.ServiceB",
                "package billing;",
                "public class ServiceB {",
                "    public ServiceB(ServiceA a) {}",
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
                .compile(serviceA, serviceB, facade);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Circular dependency detected");
        assertThat(compilation).hadErrorContaining("ServiceA -> ServiceB -> ServiceA");
    }

    @Test
    @DisplayName("Should fail when an indirect circular dependency exists (A -> B -> C -> A)")
    void shouldFailOnIndirectCircularDependency() {
        JavaFileObject serviceA = JavaFileObjects.forSourceLines(
                "billing.ServiceA",
                "package billing;",
                "public class ServiceA {",
                "    public ServiceA(ServiceB b) {}",
                "    public void doWork() {}",
                "}"
        );

        JavaFileObject serviceB = JavaFileObjects.forSourceLines(
                "billing.ServiceB",
                "package billing;",
                "public class ServiceB {",
                "    public ServiceB(ServiceC c) {}",
                "}"
        );

        JavaFileObject serviceC = JavaFileObjects.forSourceLines(
                "billing.ServiceC",
                "package billing;",
                "public class ServiceC {",
                "    public ServiceC(ServiceA a) {}",
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
                .compile(serviceA, serviceB, serviceC, facade);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Circular dependency detected");
        assertThat(compilation).hadErrorContaining("ServiceA -> ServiceB -> ServiceC -> ServiceA");
    }
}
