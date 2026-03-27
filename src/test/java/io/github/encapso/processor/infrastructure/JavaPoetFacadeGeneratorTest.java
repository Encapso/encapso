package io.github.encapso.processor.infrastructure;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.encapso.processor.ComponentProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

class JavaPoetFacadeGeneratorTest {

    @Test
    @DisplayName("Should successfully resolve namespace collisions building facade mappings without clashing")
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

    @Test
    @DisplayName("Should fundamentally translate abstract element models into physically correct literal string class declarations")
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
        
        // Asserting the exact String content natively averts edge Java 16+ module encapsulation issues
        // encountered when Google Compile Testing executes tree parsing on jdk.compiler ast dependencies.
        String generatedStr = c.generatedSourceFile("pkg.IfaceImpl")
                .orElseThrow()
                .getCharContent(false).toString();
                
        // Impl is package-private — no 'public' modifier on class or constructor
        com.google.common.truth.Truth.assertThat(generatedStr).contains("import javax.annotation.processing.Generated;");
        com.google.common.truth.Truth.assertThat(generatedStr).contains("@Generated(\"io.github.encapso.processor.ComponentProcessor\")");
        com.google.common.truth.Truth.assertThat(generatedStr).contains("final class IfaceImpl implements Iface");
        com.google.common.truth.Truth.assertThat(generatedStr).doesNotContain("public final class IfaceImpl");
        com.google.common.truth.Truth.assertThat(generatedStr).contains("private final Delegate delegate;");
        com.google.common.truth.Truth.assertThat(generatedStr).contains("IfaceImpl(Delegate delegate) {");
        com.google.common.truth.Truth.assertThat(generatedStr).doesNotContain("public IfaceImpl(Delegate delegate)");
        com.google.common.truth.Truth.assertThat(generatedStr).contains("this.delegate = delegate;");
        com.google.common.truth.Truth.assertThat(generatedStr).contains("public String run(int x) {");
        com.google.common.truth.Truth.assertThat(generatedStr).contains("return this.delegate.run(x);");

        // Builder is also generated and is public
        String builderStr = c.generatedSourceFile("pkg.IfaceBuilder")
                .orElseThrow()
                .getCharContent(false).toString();
        com.google.common.truth.Truth.assertThat(builderStr).contains("public final class IfaceBuilder");
        com.google.common.truth.Truth.assertThat(builderStr).contains("public static IfaceBuilder newBuilder()");
        com.google.common.truth.Truth.assertThat(builderStr).contains("public Iface build()");
    }
}
