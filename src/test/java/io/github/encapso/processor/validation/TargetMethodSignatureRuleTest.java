package io.github.encapso.processor.validation;

import com.google.testing.compile.Compilation;
import io.github.encapso.processor.utils.CompilerTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;

class TargetMethodSignatureRuleTest {

    @Test
    @DisplayName("Should strictly fail when target class inherently has zero methods to map")
    void shouldFailWhenTargetClassHasNoMethods() {
        Compilation c = CompilerTestUtils.compileWithDelegate(
                "public class Delegate {}",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(); }"
        );
        assertThat(c).failed();
        assertThat(c).hadErrorContaining("The target class Delegate does not have a method matching the signature: run()");
    }

    @Test
    @DisplayName("Should properly fail when target class has disparate method names mapping identically")
    void shouldFailWhenTargetClassHasWrongMethodName() {
        Compilation c = CompilerTestUtils.compileWithDelegate(
                "public class Delegate { void walk(){} void jump(){} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(); }"
        );
        assertThat(c).failed();
        assertThat(c).hadErrorContaining("does not have a method matching the signature: run()");
    }

    @Test
    @DisplayName("Should directly pass mapping exact zero-parameter symmetry")
    void shouldPassWhenTargetClassHasExactNoParamMethod() {
        Compilation c = CompilerTestUtils.compileWithDelegate(
                "public class Delegate { public void run(){} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(); }"
        );
        assertThat(c).succeeded();
    }

    @Test
    @DisplayName("Should successfully extract the precise method mapping ignoring other methods")
    void shouldPassWhenTargetClassHasTargetMethodAmongstOthers() {
        Compilation c = CompilerTestUtils.compileWithDelegate(
                "public class Delegate { void walk(){} public void run(){} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(); }"
        );
        assertThat(c).succeeded();
    }

    @Test
    @DisplayName("Should strictly fail when target signature expects no arguments but component requires one")
    void shouldFailWhenTargetClassHasZeroParamsButInterfaceExpectsOne() {
        Compilation c = CompilerTestUtils.compileWithDelegate(
                "public class Delegate { public void run(){} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(String a); }"
        );
        assertThat(c).failed();
        assertThat(c).hadErrorContaining("run(java.lang.String)");
    }

    @Test
    @DisplayName("Should instantly fail when mapped parameter types are structurally different objects")
    void shouldFailWhenTargetClassHasOneParamOfDifferentType() {
        Compilation c = CompilerTestUtils.compileWithDelegate(
                "public class Delegate { public void run(Integer a){} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(String a); }"
        );
        assertThat(c).failed();
        assertThat(c).hadErrorContaining("run(java.lang.String)");
    }

    @Test
    @DisplayName("Should completely pass dynamically mapping a single string parameter type")
    void shouldPassWhenTargetClassHasOneParamOfSameType() {
        Compilation c = CompilerTestUtils.compileWithDelegate(
                "public class Delegate { public void run(String a){} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(String a); }"
        );
        assertThat(c).succeeded();
    }

    @Test
    @DisplayName("Should correctly fail if component bounds pass less arguments than the signature strictness")
    void shouldFailWhenTargetClassHasTwoParamsButInterfaceExpectsOne() {
        Compilation c = CompilerTestUtils.compileWithDelegate(
                "public class Delegate { public void run(String a, Integer b){} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(String a); }"
        );
        assertThat(c).failed();
        assertThat(c).hadErrorContaining("run(java.lang.String)");
    }

    @Test
    @DisplayName("Should reject methods where only half the exact signature types mirror cleanly")
    void shouldFailWhenTargetClassHasTwoParamsButOneIsDifferentType() {
        Compilation c = CompilerTestUtils.compileWithDelegate(
                "public class Delegate { public void run(String a, Double b){} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(String a, Integer b); }"
        );
        assertThat(c).failed();
        assertThat(c).hadErrorContaining("run(java.lang.String,java.lang.Integer)");
    }

    @Test
    @DisplayName("Should transparently pass matching identical multi-parameter signatures")
    void shouldPassWhenTargetClassHasTwoParamsOfSameType() {
        Compilation c = CompilerTestUtils.compileWithDelegate(
                "public class Delegate { public void run(String a, Integer b){} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(String a, Integer b); }"
        );
        assertThat(c).succeeded();
    }

    @Test
    @DisplayName("Should fundamentally reject disparate return types during compilation analysis")
    void shouldFailWhenReturnTypeDiffers() {
        Compilation c = CompilerTestUtils.compileWithDelegate(
                "public class Delegate { public Integer run(){ return 1; } }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) String run(); }"
        );
        assertThat(c).failed();
        assertThat(c).hadErrorContaining("does not have a method matching the signature: run()");
    }

    @Test
    @DisplayName("Should heavily reject mapping checked IO exceptions across mismatched signature throws constraints")
    void shouldFailWhenThrownExceptionsDiffer() {
        Compilation c = CompilerTestUtils.compileWithDelegate(
                "public class Delegate { public void run() throws java.io.IOException {} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run() throws java.sql.SQLException; }"
        );
        assertThat(c).failed();
        assertThat(c).hadErrorContaining("does not have a method matching the signature: run()");
    }

    @Test
    @DisplayName("Should strictly fail when target signature is static")
    void shouldFailWhenTargetMethodIsStatic() {
        Compilation c = CompilerTestUtils.compileWithDelegate(
                "public class Delegate { public static void run(){} }",
                "@Component public interface Iface { @DelegateTo(Delegate.class) void run(); }"
        );
        assertThat(c).failed();
        assertThat(c).hadErrorContaining("is static and cannot be used for delegation. Encapso only supports instance-based delegating.");
    }
}
