package io.github.encapso.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;
import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

class ComponentProcessorTest {

    @Test
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

    private Compilation compileWithDelegate(String delegateContent, String interfaceContent) {
        JavaFileObject delegate = JavaFileObjects.forSourceLines(
                "test.Delegate",
                "package test;",
                delegateContent
        );
        JavaFileObject iface = JavaFileObjects.forSourceLines(
                "test.Iface",
                "package test;",
                "import io.github.encapso.*;",
                interfaceContent
        );
        return javac()
                .withProcessors(new ComponentProcessor())
                .compile(delegate, iface);
    }

    // 1. The class has no methods. - error
    @Test
    void shouldFailWhenTargetClassHasNoMethods() {
        Compilation c = compileWithDelegate(
                "public class Delegate {}",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(); }"
        );
        assertThat(c).failed();
        assertThat(c).hadErrorContaining("The target class Delegate does not have a method matching the signature: run()");
    }

    // 2. The class has many methods, but no methods named as expected - error
    @Test
    void shouldFailWhenTargetClassHasWrongMethodName() {
        Compilation c = compileWithDelegate(
                "public class Delegate { void walk(){} void jump(){} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(); }"
        );
        assertThat(c).failed();
        assertThat(c).hadErrorContaining("The target class Delegate does not have a method matching the signature: run()");
    }

    // 3. The class has only the method we expect with no params (we expect no params) - no error
    @Test
    void shouldPassWhenTargetClassHasExactNoParamMethod() {
        Compilation c = compileWithDelegate(
                "public class Delegate { public void run(){} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(); }"
        );
        assertThat(c).succeeded();
    }

    // 4. The class has methods and the method we expect with no params (we expect no params) - no error
    @Test
    void shouldPassWhenTargetClassHasTargetMethodAmongstOthers() {
        Compilation c = compileWithDelegate(
                "public class Delegate { void walk(){} public void run(){} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(); }"
        );
        assertThat(c).succeeded();
    }

    // 5. Has: 0 Expect: 1 - error
    @Test
    void shouldFailWhenTargetClassHasZeroParamsButInterfaceExpectsOne() {
        Compilation c = compileWithDelegate(
                "public class Delegate { public void run(){} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(String a); }"
        );
        assertThat(c).failed();
        assertThat(c).hadErrorContaining("The target class Delegate does not have a method matching the signature: run(java.lang.String)");
    }

    // 6. Has: 1 Expect: 1; different type - error
    @Test
    void shouldFailWhenTargetClassHasOneParamOfDifferentType() {
        Compilation c = compileWithDelegate(
                "public class Delegate { public void run(Integer a){} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(String a); }"
        );
        assertThat(c).failed();
        assertThat(c).hadErrorContaining("The target class Delegate does not have a method matching the signature: run(java.lang.String)");
    }

    // 7. Has: 1 Expect: 1; same type - no error
    @Test
    void shouldPassWhenTargetClassHasOneParamOfSameType() {
        Compilation c = compileWithDelegate(
                "public class Delegate { public void run(String a){} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(String a); }"
        );
        assertThat(c).succeeded();
    }

    // 8. Has: 2 Expect: 1 - error
    @Test
    void shouldFailWhenTargetClassHasTwoParamsButInterfaceExpectsOne() {
        Compilation c = compileWithDelegate(
                "public class Delegate { public void run(String a, Integer b){} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(String a); }"
        );
        assertThat(c).failed();
        assertThat(c).hadErrorContaining("The target class Delegate does not have a method matching the signature: run(java.lang.String)");
    }

    // 9. Has: 2 Expect: 2; one has different type - error
    @Test
    void shouldFailWhenTargetClassHasTwoParamsButOneIsDifferentType() {
        Compilation c = compileWithDelegate(
                "public class Delegate { public void run(String a, Double b){} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(String a, Integer b); }"
        );
        assertThat(c).failed();
        assertThat(c).hadErrorContaining("The target class Delegate does not have a method matching the signature: run(java.lang.String,java.lang.Integer)");
    }

    // 10. Has: 2 Expect: 2; same types - no error
    @Test
    void shouldPassWhenTargetClassHasTwoParamsOfSameType() {
        Compilation c = compileWithDelegate(
                "public class Delegate { public void run(String a, Integer b){} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(String a, Integer b); }"
        );
        assertThat(c).succeeded();
    }
    // 11. the TC is outside - error
    @Test
    void shouldFailWhenTargetClassIsOutsideInterfacePackage() {
        JavaFileObject delegate = JavaFileObjects.forSourceLines(
                "other.Delegate",
                "package other;",
                "public class Delegate { public void run(){} }"
        );
        JavaFileObject iface = JavaFileObjects.forSourceLines(
                "pkg.Iface",
                "package pkg;",
                "import io.github.encapso.*;",
                "import other.Delegate;",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(); }"
        );
        Compilation c = javac()
                .withProcessors(new ComponentProcessor())
                .compile(delegate, iface);
        
        assertThat(c).failed();
        assertThat(c).hadErrorContaining("The target class Delegate must be inside the interface package pkg or a subpackage.");
    }

    // 12. the TC is in a subpackage - no error
    @Test
    void shouldPassWhenTargetClassIsInSubpackage() {
        JavaFileObject delegate = JavaFileObjects.forSourceLines(
                "pkg.sub.Delegate",
                "package pkg.sub;",
                "public class Delegate { public void run(){} }"
        );
        JavaFileObject iface = JavaFileObjects.forSourceLines(
                "pkg.Iface",
                "package pkg;",
                "import io.github.encapso.*;",
                "import pkg.sub.Delegate;",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(); }"
        );
        Compilation c = javac()
                .withProcessors(new ComponentProcessor())
                .compile(delegate, iface);
        
        assertThat(c).succeeded();
    }
    // 13. Rule 1.2: TC in subpackage must be public, package-private fails
    @Test
    void shouldFailWhenTargetClassInSubpackageIsPackagePrivate() {
        JavaFileObject delegate = JavaFileObjects.forSourceLines(
                "pkg.sub.Delegate",
                "package pkg.sub;",
                "// Lack of public modifier makes it package-private",
                "class Delegate { public void run(){} }"
        );
        JavaFileObject iface = JavaFileObjects.forSourceLines(
                "pkg.Iface",
                "package pkg;",
                "import io.github.encapso.*;",
                "import pkg.sub.Delegate;",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(); }"
        );
        Compilation c = javac()
                .withProcessors(new ComponentProcessor())
                .compile(delegate, iface);

        assertThat(c).failed();
        assertThat(c).hadErrorContaining("cannot be accessed from outside package");
    }

    // 14. Rule 2.1: Mismatched Return Type fails
    @Test
    void shouldFailWhenReturnTypeDiffers() {
        Compilation c = compileWithDelegate(
                "public class Delegate { public Integer run(){ return 1; } }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) String run(); }"
        );
        assertThat(c).failed();
        assertThat(c).hadErrorContaining("does not have a method matching the signature: run()");
    }

    // 15. Rule 2.1: Mismatched Checked Exceptions fails
    @Test
    void shouldFailWhenThrownExceptionsDiffer() {
        Compilation c = compileWithDelegate(
                "public class Delegate { public void run() throws java.io.IOException {} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run() throws java.sql.SQLException; }"
        );
        assertThat(c).failed();
        assertThat(c).hadErrorContaining("does not have a method matching the signature: run()");
    }

    // 16. Rule 2.3: Return type is public
    @Test
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

    // 17. Rule 2.4: Thrown exception is public
    @Test
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

    // 18. Duplicate Class Names in Component
    @Test
    void shouldHandleDuplicateTargetClassNamesInDifferentPackages() {
        JavaFileObject delegateA = JavaFileObjects.forSourceLines(
                "pkg.a.Delegate",
                "package pkg.a;",
                "public class Delegate { public void runA(){} }"
        );
        JavaFileObject delegateB = JavaFileObjects.forSourceLines(
                "pkg.b.Delegate",
                "package pkg.b;",
                "public class Delegate { public void runB(){} }"
        );
        JavaFileObject iface = JavaFileObjects.forSourceLines(
                "pkg.Iface",
                "package pkg;",
                "import io.github.encapso.*;",
                "@Component public interface Iface {",
                "   @DelegateTo(pkg.a.Delegate.class) void runA();",
                "   @DelegateTo(pkg.b.Delegate.class) void runB();",
                "}"
        );
        Compilation c = javac()
                .withProcessors(new ComponentProcessor())
                .compile(delegateA, delegateB, iface);

        assertThat(c).succeeded();
    }

    // 19. Validate generated JavaPoet output structurally
    @Test
    void shouldGenerateExpectedFacadeClass() throws Exception {
        JavaFileObject delegate = JavaFileObjects.forSourceLines(
                "pkg.Delegate",
                "package pkg;",
                "public class Delegate { public String run(int x){ return null; } }"
        );
        JavaFileObject iface = JavaFileObjects.forSourceLines(
                "pkg.Iface",
                "package pkg;",
                "import io.github.encapso.*;",
                "@Component public interface Iface { @DelegateTo(Delegate.class) String run(int x); }"
        );

        Compilation c = javac()
                .withProcessors(new ComponentProcessor())
                .compile(delegate, iface);

        assertThat(c).succeeded();
        
        // Asserting the exact String content manually avoids Java 16+ module encapsulation errors
        // that occur when Google Compile Testing tries to open jdk.compiler for AST matching.
        String generatedStr = c.generatedSourceFile("pkg.IfaceImpl")
                .orElseThrow()
                .getCharContent(false).toString();
                
        com.google.common.truth.Truth.assertThat(generatedStr).contains("import javax.annotation.processing.Generated;");
        com.google.common.truth.Truth.assertThat(generatedStr).contains("@Generated(\"io.github.encapso.processor.ComponentProcessor\")");
        com.google.common.truth.Truth.assertThat(generatedStr).contains("public final class IfaceImpl implements Iface");
        com.google.common.truth.Truth.assertThat(generatedStr).contains("private final Delegate delegate;");
        com.google.common.truth.Truth.assertThat(generatedStr).contains("public IfaceImpl(Delegate delegate) {");
        com.google.common.truth.Truth.assertThat(generatedStr).contains("this.delegate = delegate;");
        com.google.common.truth.Truth.assertThat(generatedStr).contains("public String run(int x) {");
        com.google.common.truth.Truth.assertThat(generatedStr).contains("return this.delegate.run(x);");
    }
}
