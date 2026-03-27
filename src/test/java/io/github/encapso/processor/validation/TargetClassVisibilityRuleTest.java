package io.github.encapso.processor.validation;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.encapso.processor.ComponentProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

class TargetClassVisibilityRuleTest {

    // 11. the TC is outside - error
    @Test
    @DisplayName("Should strictly fail when target class is entirely outside the interface boundary package")
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
    @DisplayName("Should seamlessly pass when target class resides cleanly inside a nested subpackage")
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
    @DisplayName("Should strictly fail if a subpackage target reduces access modifiers below public visibility")
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
}
