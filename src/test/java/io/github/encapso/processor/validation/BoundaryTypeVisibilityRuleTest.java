package io.github.encapso.processor.validation;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.encapso.processor.ComponentProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

class BoundaryTypeVisibilityRuleTest {

    @Test
    @DisplayName("Should strictly fail when complex nested object argument drops below public access levels")
    void shouldFailCompilationWhenParameterIsNotPublic() {
        JavaFileObject internalRequestClass = JavaFileObjects.forSourceLines(
                "com.gmail.jordansilva.billing.InternalRequest",
                "package com.gmail.jordansilva.billing;",
                "",
                "// Notice this class is package-private (no public modifier)",
                "class InternalRequest {",
                "}"
        );

        JavaFileObject internalUseCaseClass = JavaFileObjects.forSourceLines(
                "com.gmail.jordansilva.billing.ProcessRequestUseCase",
                "package com.gmail.jordansilva.billing;",
                "",
                "class ProcessRequestUseCase {",
                "    public void execute(InternalRequest request) {}",
                "}"
        );

        JavaFileObject facadeInterface = JavaFileObjects.forSourceLines(
                "com.gmail.jordansilva.billing.BillingFacade",
                "package com.gmail.jordansilva.billing;",
                "",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "",
                "@Component",
                "public interface BillingFacade {",
                "    @DelegateTo(ProcessRequestUseCase.class)",
                "    void process(InternalRequest request);",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(internalRequestClass, internalUseCaseClass, facadeInterface);

        assertThat(compilation).failed();
        assertThat(compilation)
                .hadErrorContaining("The parameter 'InternalRequest' used in BillingFacade.process() must be public because it is exposed through the component boundary.")
                .inFile(facadeInterface)
                .onLine(9);
    }

    @Test
    @DisplayName("Should natively block package-private object returns crossing the domain output boundary")
    void shouldFailWhenReturnTypeIsNotPublic() {
        JavaFileObject internalReturn = JavaFileObjects.forSourceLines(
                "test.InternalReturn",
                "package test;",
                "class InternalReturn {}"
        );
        JavaFileObject delegate = JavaFileObjects.forSourceLines(
                "test.Delegate",
                "package test;",
                "public class Delegate { public InternalReturn run() { return null; } }"
        );
        JavaFileObject iface = JavaFileObjects.forSourceLines(
                "test.Iface",
                "package test;",
                "import io.github.encapso.*;",
                "@Component public interface Iface { @DelegateTo(Delegate.class) InternalReturn run(); }"
        );
        Compilation c = javac()
                .withProcessors(new ComponentProcessor())
                .compile(internalReturn, delegate, iface);

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("The return type 'InternalReturn' used in Iface.run() must be public because it is exposed through the component boundary.");
    }

    @Test
    @DisplayName("Should brutally reject mapping tightly scoped custom Exception throws back across boundaries")
    void shouldFailWhenThrownExceptionIsNotPublic() {
        JavaFileObject internalException = JavaFileObjects.forSourceLines(
                "test.InternalException",
                "package test;",
                "class InternalException extends Exception {}"
        );
        JavaFileObject delegate = JavaFileObjects.forSourceLines(
                "test.Delegate",
                "package test;",
                "public class Delegate { public void run() throws InternalException {} }"
        );
        JavaFileObject iface = JavaFileObjects.forSourceLines(
                "test.Iface",
                "package test;",
                "import io.github.encapso.*;",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run() throws InternalException; }"
        );
        Compilation c = javac()
                .withProcessors(new ComponentProcessor())
                .compile(internalException, delegate, iface);

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("The thrown exception 'InternalException' used in Iface.run() must be public because it is exposed through the component boundary.");
    }
}
