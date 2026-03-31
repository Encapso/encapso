package io.github.encapso.processor.validation;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.encapso.processor.ComponentProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

class EncapsulationHardeningTest {

    private static final JavaFileObject INTERNAL_SERVICE = JavaFileObjects.forSourceLines(
            "component.internal.InternalService",
            "package component.internal;",
            "public class InternalService {",
            "    public void doSomething() {}",
            "}"
    );

    private static final JavaFileObject PUBLIC_INTERNAL_SERVICE = JavaFileObjects.forSourceLines(
            "component.internal.PublicInternalService",
            "package component.internal;",
            "import io.github.encapso.Api;",
            "@Api",
            "public class PublicInternalService {",
            "    public void doSomething() {}",
            "    public void doPublic() {}",
            "}"
    );

    private static final JavaFileObject COMPONENT_INTERFACE = JavaFileObjects.forSourceLines(
            "component.MyComponent",
            "package component;",
            "import io.github.encapso.Component;",
            "import io.github.encapso.DelegateTo;",
            "import component.internal.InternalService;",
            "@Component",
            "public interface MyComponent {",
            "    @DelegateTo(InternalService.class)",
            "    void doSomething();",
            "}"
    );

    @Test
    @DisplayName("Should fail when an external class imports an internal type")
    void shouldFailOnInternalImport() {
        JavaFileObject external = JavaFileObjects.forSourceLines(
                "other.ExternalService",
                "package other;",
                "import component.internal.InternalService;",
                "public class ExternalService {",
                "    public void use() {",
                "        new InternalService().doSomething();",
                "    }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(COMPONENT_INTERFACE, INTERNAL_SERVICE, external);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Class 'InternalService' is an internal implementation detail of the 'component' component");
    }

    @Test
    @DisplayName("Should succeed when an external class imports an @Api annotated internal type")
    void shouldSucceedOnApiImport() {
        JavaFileObject external = JavaFileObjects.forSourceLines(
                "other.ExternalService",
                "package other;",
                "import component.internal.PublicInternalService;",
                "public class ExternalService {",
                "    public void use(PublicInternalService svc) {",
                "        svc.doPublic();",
                "    }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(COMPONENT_INTERFACE, PUBLIC_INTERNAL_SERVICE, INTERNAL_SERVICE, external);

        assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Should fail when an external class uses an internal type in a method body (local variable)")
    void shouldFailOnInternalLocalVariable() {
        JavaFileObject external = JavaFileObjects.forSourceLines(
                "other.ExternalService",
                "package other;",
                "import component.MyComponent;",
                "public class ExternalService {",
                "    public void use() {",
                "        component.internal.InternalService svc = null;",
                "    }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(COMPONENT_INTERFACE, INTERNAL_SERVICE, external);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Class 'InternalService' is an internal implementation detail of the 'component' component");
    }

    @Test
    @DisplayName("Should fail when an external class instantiates an internal type in a method body")
    void shouldFailOnInternalInstantiation() {
        JavaFileObject external = JavaFileObjects.forSourceLines(
                "other.ExternalService",
                "package other;",
                "public class ExternalService {",
                "    public void use() {",
                "        Object o = new component.internal.InternalService();",
                "    }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(COMPONENT_INTERFACE, INTERNAL_SERVICE, external);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Class 'InternalService' is an internal implementation detail of the 'component' component");
    }

    @Test
    @DisplayName("Should fail on wildcard import of internal package")
    void shouldFailOnWildcardInternalImport() {
        JavaFileObject external = JavaFileObjects.forSourceLines(
                "other.ExternalService",
                "package other;",
                "import component.internal.*;",
                "public class ExternalService {",
                "    public void use() {",
                "        InternalService svc = new InternalService();",
                "    }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(COMPONENT_INTERFACE, INTERNAL_SERVICE, external);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Class 'InternalService' is an internal implementation detail of the 'component' component");
    }

    @Test
    @DisplayName("Should succeed when using @Api type inside method body")
    void shouldSucceedOnApiUsageInMethod() {
        JavaFileObject external = JavaFileObjects.forSourceLines(
                "other.ExternalService",
                "package other;",
                "public class ExternalService {",
                "    public void use() {",
                "        component.internal.PublicInternalService svc = new component.internal.PublicInternalService();",
                "        svc.doPublic();",
                "    }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(COMPONENT_INTERFACE, PUBLIC_INTERNAL_SERVICE, INTERNAL_SERVICE, external);

        assertThat(compilation).succeeded();
    }
}
