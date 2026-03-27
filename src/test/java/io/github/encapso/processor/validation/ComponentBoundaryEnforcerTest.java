package io.github.encapso.processor.validation;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.encapso.processor.ComponentProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

/**
 * Tests for the automatic component boundary enforcement.
 *
 * The rule: any class inside a @Component package that is not the interface,
 * the builder, or a type appearing in method signatures cannot be referenced
 * from outside the component package.
 */
class ComponentBoundaryEnforcerTest {

    private static final String VIOLATION_MSG = "internal implementation detail";

    // -------------------------------------------------------------------------
    // Violation cases
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Violations — internal class referenced from outside the component")
    class ViolationCases {

        @Test
        @DisplayName("Should fail when external class declares a field of an internal type")
        void shouldFailWhenExternalFieldUsesInternalType() {
            JavaFileObject external = JavaFileObjects.forSourceLines(
                    "ui.CheckoutController",
                    "package ui;",
                    "import billing.InvoiceService;",
                    "public class CheckoutController {",
                    "    private InvoiceService service;",   // ← violation
                    "}"
            );
            assertCompilationFails(external);
        }

        @Test
        @DisplayName("Should fail when external method takes an internal type as a parameter")
        void shouldFailWhenExternalMethodParamIsInternalType() {
            JavaFileObject external = JavaFileObjects.forSourceLines(
                    "ui.CheckoutController",
                    "package ui;",
                    "import billing.InvoiceService;",
                    "public class CheckoutController {",
                    "    public void process(InvoiceService svc) {}",  // ← violation
                    "}"
            );
            assertCompilationFails(external);
        }

        @Test
        @DisplayName("Should fail when external method returns an internal type")
        void shouldFailWhenExternalMethodReturnsInternalType() {
            JavaFileObject external = JavaFileObjects.forSourceLines(
                    "ui.CheckoutController",
                    "package ui;",
                    "import billing.InvoiceService;",
                    "public class CheckoutController {",
                    "    public InvoiceService getService() { return null; }",  // ← violation
                    "}"
            );
            assertCompilationFails(external);
        }

        @Test
        @DisplayName("Should fail when external constructor takes an internal type as parameter")
        void shouldFailWhenExternalConstructorUsesInternalType() {
            JavaFileObject external = JavaFileObjects.forSourceLines(
                    "ui.CheckoutController",
                    "package ui;",
                    "import billing.InvoiceService;",
                    "public class CheckoutController {",
                    "    public CheckoutController(InvoiceService svc) {}",  // ← violation
                    "}"
            );
            assertCompilationFails(external);
        }

        @Test
        @DisplayName("Should fail when an internal type is used as a generic argument externally")
        void shouldFailWhenInternalTypeAppearsAsGenericArgument() {
            JavaFileObject external = JavaFileObjects.forSourceLines(
                    "ui.CheckoutController",
                    "package ui;",
                    "import billing.InvoiceService;",
                    "import java.util.List;",
                    "public class CheckoutController {",
                    "    public List<InvoiceService> listServices() { return null; }",  // ← violation
                    "}"
            );
            assertCompilationFails(external);
        }

        @Test
        @DisplayName("Should fail when an external class extends an internal component class")
        void shouldFailWhenExternalClassExtendsInternalClass() {
            JavaFileObject external = JavaFileObjects.forSourceLines(
                    "ui.ExtendedService",
                    "package ui;",
                    "import billing.InvoiceService;",
                    "public class ExtendedService extends InvoiceService {}"  // ← violation
            );
            assertCompilationFails(external);
        }

        @Test
        @DisplayName("Should fail when an external method throws an internal exception not declared in the facade signature")
        void shouldFailWhenExternalMethodThrowsInternalException() {
            JavaFileObject internalException = JavaFileObjects.forSourceLines(
                    "billing.InternalException",
                    "package billing;",
                    "public class InternalException extends Exception {}"
            );
            JavaFileObject external = JavaFileObjects.forSourceLines(
                    "ui.CheckoutController",
                    "package ui;",
                    "import billing.InternalException;",
                    "public class CheckoutController {",
                    "    public void process() throws InternalException {}",  // ← violation
                    "}"
            );
            assertCompilationFails(external, internalException);
        }
    }

    // -------------------------------------------------------------------------
    // Allowed cases
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Allowed usages — facade interface, builder, signature types, and same-package code")
    class AllowedCases {

        @Test
        @DisplayName("Should allow external class to hold the @Component interface as a field")
        void shouldAllowFacadeInterfaceAsField() {
            JavaFileObject external = JavaFileObjects.forSourceLines(
                    "ui.CheckoutController",
                    "package ui;",
                    "import billing.BillingFacade;",
                    "public class CheckoutController {",
                    "    private final BillingFacade facade;",
                    "    public CheckoutController(BillingFacade facade) { this.facade = facade; }",
                    "}"
            );
            assertCompilationSucceeds(getInternalService(), getBillingFacade(), external);
        }

        @Test
        @DisplayName("Should allow external class to use the generated builder to construct the facade")
        void shouldAllowBuilderUsage() {
            JavaFileObject external = JavaFileObjects.forSourceLines(
                    "ui.App",
                    "package ui;",
                    "import billing.BillingFacade;",
                    "import billing.BillingFacadeBuilder;",
                    "public class App {",
                    "    public BillingFacade create() {",
                    "        return BillingFacadeBuilder.newBuilder().build();",
                    "    }",
                    "}"
            );
            assertCompilationSucceeds(getInternalService(), getBillingFacade(), external);
        }

        @Test
        @DisplayName("Should allow code inside the component package to reference any internal class")
        void shouldAllowSamePackageCodeToUseInternalClasses() {
            JavaFileObject internalCaller = JavaFileObjects.forSourceLines(
                    "billing.BillingHelper",
                    "package billing;",
                    "public class BillingHelper {",
                    "    private InvoiceService service;",
                    "}"
            );
            assertCompilationSucceeds(getInternalService(), getBillingFacade(), internalCaller);
        }

        @Test
        @DisplayName("Should allow external code to use a type that appears as a return type in the facade")
        void shouldAllowReturnTypeFromFacadeSignature() {
            JavaFileObject resultDto = JavaFileObjects.forSourceLines(
                    "billing.InvoiceResult", "package billing;", "public class InvoiceResult {}");

            JavaFileObject service = JavaFileObjects.forSourceLines(
                    "billing.InvoiceService",
                    "package billing;",
                    "public class InvoiceService {",
                    "    public InvoiceResult calculate() { return null; }",
                    "}"
            );

            JavaFileObject facade = JavaFileObjects.forSourceLines(
                    "billing.BillingFacade",
                    "package billing;",
                    "import io.github.encapso.Component;",
                    "import io.github.encapso.DelegateTo;",
                    "@Component",
                    "public interface BillingFacade {",
                    "    @DelegateTo(InvoiceService.class)",
                    "    InvoiceResult calculate();",
                    "}"
            );

            JavaFileObject external = JavaFileObjects.forSourceLines(
                    "ui.CheckoutController",
                    "package ui;",
                    "import billing.BillingFacade;",
                    "import billing.InvoiceResult;",
                    "public class CheckoutController {",
                    "    public InvoiceResult doCheckout(BillingFacade facade) { return facade.calculate(); }",
                    "}"
            );

            assertCompilationSucceeds(service, facade, resultDto, external);
        }

        @Test
        @DisplayName("Should allow external code to use a type that appears as a parameter in the facade")
        void shouldAllowParameterTypeFromFacadeSignature() {
            JavaFileObject requestDto = JavaFileObjects.forSourceLines(
                    "billing.InvoiceRequest", "package billing;", "public class InvoiceRequest {}");

            JavaFileObject service = JavaFileObjects.forSourceLines(
                    "billing.InvoiceService",
                    "package billing;",
                    "public class InvoiceService {",
                    "    public String calculate(InvoiceRequest req) { return null; }",
                    "}"
            );

            JavaFileObject facade = JavaFileObjects.forSourceLines(
                    "billing.BillingFacade",
                    "package billing;",
                    "import io.github.encapso.Component;",
                    "import io.github.encapso.DelegateTo;",
                    "@Component",
                    "public interface BillingFacade {",
                    "    @DelegateTo(InvoiceService.class)",
                    "    String calculate(InvoiceRequest req);",
                    "}"
            );

            JavaFileObject external = JavaFileObjects.forSourceLines(
                    "ui.CheckoutController",
                    "package ui;",
                    "import billing.BillingFacade;",
                    "import billing.InvoiceRequest;",
                    "public class CheckoutController {",
                    "    public void doCheckout(BillingFacade f, InvoiceRequest r) { f.calculate(r); }",
                    "}"
            );

            assertCompilationSucceeds(service, facade, requestDto, external);
        }

        @Test
        @DisplayName("Should allow external code to declare throws for an exception in the facade signature")
        void shouldAllowExceptionTypeFromFacadeSignature() {
            JavaFileObject publicException = JavaFileObjects.forSourceLines(
                    "billing.BillingException", "package billing;", "public class BillingException extends Exception {}");

            JavaFileObject service = JavaFileObjects.forSourceLines(
                    "billing.InvoiceService",
                    "package billing;",
                    "public class InvoiceService {",
                    "    public String calculate() throws BillingException { return null; }",
                    "}"
            );

            JavaFileObject facade = JavaFileObjects.forSourceLines(
                    "billing.BillingFacade",
                    "package billing;",
                    "import io.github.encapso.Component;",
                    "import io.github.encapso.DelegateTo;",
                    "@Component",
                    "public interface BillingFacade {",
                    "    @DelegateTo(InvoiceService.class)",
                    "    String calculate() throws BillingException;",
                    "}"
            );

            JavaFileObject external = JavaFileObjects.forSourceLines(
                    "ui.CheckoutController",
                    "package ui;",
                    "import billing.BillingFacade;",
                    "import billing.BillingException;",
                    "public class CheckoutController {",
                    "    public void doCheckout(BillingFacade f) throws BillingException { f.calculate(); }",
                    "}"
            );

            assertCompilationSucceeds(service, facade, publicException, external);
        }

        @Test
        @DisplayName("Should allow external code to use an internal type explicitly marked with @Api")
        void shouldAllowApiAnnotatedType() {
            // InvoiceStatus is NOT in any method signature, but marked with @Api
            JavaFileObject apiEnum = JavaFileObjects.forSourceLines(
                    "billing.InvoiceStatus",
                    "package billing;",
                    "import io.github.encapso.Api;",
                    "@Api",
                    "public enum InvoiceStatus { PAID, PENDING }"
            );

            JavaFileObject external = JavaFileObjects.forSourceLines(
                    "ui.CheckoutController",
                    "package ui;",
                    "import billing.InvoiceStatus;",
                    "public class CheckoutController {",
                    "    private InvoiceStatus status = InvoiceStatus.PAID;",
                    "}"
            );

            assertCompilationSucceeds(apiEnum, external);
        }
    }

    // --- Helper Assertion Methods ---

    private void assertCompilationFails(JavaFileObject... files) {
        // Violation tests always use the standard internal service and facade
        Compilation c = compile(addAll(getInternalService(), getBillingFacade(), files));
        assertThat(c).failed();
        assertThat(c).hadErrorContaining(VIOLATION_MSG);
    }

    private void assertCompilationSucceeds(JavaFileObject... files) {
        // Allowed tests are explicit about what they need
        Compilation c = compile(files);
        assertThat(c).succeeded();
    }

    private JavaFileObject[] addAll(JavaFileObject service, JavaFileObject facade, JavaFileObject... others) {
        List<JavaFileObject> all = new ArrayList<>();
        all.add(service);
        all.add(facade);
        all.addAll(Arrays.asList(others));
        return all.toArray(new JavaFileObject[0]);
    }

    private Compilation compile(JavaFileObject... files) {
        return javac().withProcessors(new ComponentProcessor())
                .compile(files);
    }

    private JavaFileObject getInternalService() {
        return JavaFileObjects.forSourceLines(
                "billing.InvoiceService",
                "package billing;",
                "public class InvoiceService {",
                "    public String calculate() { return \"$100\"; }",
                "}"
        );
    }

    private JavaFileObject getBillingFacade() {
        return JavaFileObjects.forSourceLines(
                "billing.BillingFacade",
                "package billing;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "@Component",
                "public interface BillingFacade {",
                "    @DelegateTo(InvoiceService.class)",
                "    String calculate();",
                "}"
        );
    }
}
