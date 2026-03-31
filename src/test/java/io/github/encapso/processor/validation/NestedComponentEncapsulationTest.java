package io.github.encapso.processor.validation;

import com.google.testing.compile.JavaFileObjects;
import io.github.encapso.processor.ComponentProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.Compiler.javac;
import static com.google.testing.compile.CompilationSubject.assertThat;

class NestedComponentEncapsulationTest {

    @Test
    @DisplayName("Should fail if Parent component uses Internal class of Child component")
    void shouldFailIfParentUsesChildInternal() {
        JavaFileObject parent = JavaFileObjects.forSourceLines(
                "com.foo.ParentComponent",
                "package com.foo;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "import com.foo.child.ChildInternal;",
                "@Component",
                "public interface ParentComponent {",
                "    @DelegateTo(ParentTarget.class) void doParent();",
                "    class ParentTarget {",
                "        public ParentTarget(ChildInternal child) {} // VIOLATION",
                "        public void doParent() {}",
                "    }",
                "}"
        );

        JavaFileObject child = JavaFileObjects.forSourceLines(
                "com.foo.child.ChildComponent",
                "package com.foo.child;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "@Component",
                "public interface ChildComponent {",
                "    @DelegateTo(ChildInternal.class) void doChild();",
                "}"
        );

        JavaFileObject childInternal = JavaFileObjects.forSourceLines(
                "com.foo.child.ChildInternal",
                "package com.foo.child;",
                "public class ChildInternal {",
                "    public void doChild() {}",
                "}"
        );

        var compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(parent, child, childInternal);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Class 'ChildInternal' is an internal implementation detail of the 'com.foo.child' component");
    }

    @Test
    @DisplayName("Should fail if Child component uses Internal class of Parent component")
    void shouldFailIfChildUsesParentInternal() {
        JavaFileObject parent = JavaFileObjects.forSourceLines(
                "com.foo.ParentComponent",
                "package com.foo;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "@Component",
                "public interface ParentComponent {",
                "    @DelegateTo(ParentInternal.class) void doParent();",
                "}"
        );

        JavaFileObject parentInternal = JavaFileObjects.forSourceLines(
                "com.foo.ParentInternal",
                "package com.foo;",
                "public class ParentInternal {",
                "    public void doParent() {}",
                "}"
        );

        JavaFileObject child = JavaFileObjects.forSourceLines(
                "com.foo.child.ChildComponent",
                "package com.foo.child;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "import com.foo.ParentInternal;",
                "@Component",
                "public interface ChildComponent {",
                "    @DelegateTo(ChildTarget.class) void doChild();",
                "    class ChildTarget {",
                "        public ChildTarget(ParentInternal parent) {} // VIOLATION",
                "        public void doChild() {}",
                "    }",
                "}"
        );

        var compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(parent, parentInternal, child);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Class 'ParentInternal' is an internal implementation detail of the 'com.foo' component");
    }

    @Test
    @DisplayName("Should succeed if Parent uses Child public interface (Boundary)")
    void shouldSucceedIfParentUsesChildBoundary() {
        JavaFileObject parent = JavaFileObjects.forSourceLines(
                "com.foo.ParentComponent",
                "package com.foo;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "import com.foo.child.ChildComponent;",
                "@Component",
                "public interface ParentComponent {",
                "    @DelegateTo(ParentTarget.class) void doParent();",
                "    class ParentTarget {",
                "        public ParentTarget(ChildComponent child) {} // ALLOWED: Calling the interface",
                "        public void doParent() {}",
                "    }",
                "}"
        );

        JavaFileObject child = JavaFileObjects.forSourceLines(
                "com.foo.child.ChildComponent",
                "package com.foo.child;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "@Component",
                "public interface ChildComponent {",
                "    @DelegateTo(ChildTarget.class) void doChild();",
                "    class ChildTarget {",
                "        public void doChild() {}",
                "    }",
                "}"
        );

        var compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(parent, child);

        assertThat(compilation).succeeded();
    }
}
