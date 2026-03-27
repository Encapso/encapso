package io.github.encapso.processor.utils;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.encapso.processor.ComponentProcessor;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.Compiler.javac;

/**
 * Shared test framework bootstrap to eliminate compilation duplication across isolated rule domains.
 */
public class CompilerTestUtils {

    /**
     * Bootstraps a ComponentProcessor native JVM execution against the provided source blocks.
     */
    public static Compilation compileWithDelegate(String delegateContent, String interfaceContent) {
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
}
