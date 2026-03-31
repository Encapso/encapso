package io.github.encapso.integration;

import io.github.encapso.integration.ecommerce.ECommerceFacade;
import io.github.encapso.integration.ecommerce.ECommerceFacadeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ECommerce integration test — rewritten to use the generated ComponentBuilder.
 *
 * OrderService, PaymentService, and InventoryService have no constructor dependencies
 * (all internal, zero-arg), so the builder requires no external setters.
 * Tests are now purely black-box: only the facade interface is visible.
 */
class ServiceDelegationIntegrationTest {

    private ECommerceFacade facade;

    @BeforeEach
    void setUp() {
        // No external deps needed — builder calls default constructors internally
        facade = ECommerceFacadeBuilder.newBuilder().build();
    }

    @Nested
    @DisplayName("OrderService delegation")
    class OrderServiceTests {

        @Test
        @DisplayName("Should delegate place(String) to the single-unit overload")
        void shouldDelegateSingleUnitPlaceOverload() {
            assertEquals("ORD-SKU-001-1", facade.place("SKU-001"));
        }

        @Test
        @DisplayName("Should delegate place(String, int) to the quantity overload")
        void shouldDelegateQuantityPlaceOverload() {
            assertEquals("ORD-SKU-002-5", facade.place("SKU-002", 5));
        }

        @Test
        @DisplayName("Should dispatch both place() overloads independently")
        void shouldDispatchBothOverloadsIndependently() {
            String single = facade.place("SKU-A");
            String multi = facade.place("SKU-B", 3);

            assertEquals("ORD-SKU-A-1", single);
            assertEquals("ORD-SKU-B-3", multi);
        }

        @Test
        @DisplayName("Should delegate cancel() and return true")
        void shouldDelegateCancelToOrderService() {
            assertTrue(facade.cancel("ORD-XYZ"));
        }
    }

    @Nested
    @DisplayName("PaymentService delegation")
    class PaymentServiceTests {

        @Test
        @DisplayName("Should delegate charge() and return a receipt")
        void shouldDelegateChargeToPaymentService() {
            assertEquals("CHARGE-ORD-001-99.99", facade.charge("ORD-001", 99.99));
        }

        @Test
        @DisplayName("Should delegate refund() and return a refund receipt")
        void shouldDelegateRefundToPaymentService() {
            assertEquals("REFUND-ORD-002", facade.refund("ORD-002"));
        }
    }

    @Nested
    @DisplayName("InventoryService delegation")
    class InventoryServiceTests {

        @Test
        @DisplayName("Should return true when reserving a valid quantity")
        void shouldReturnTrueForValidReservation() {
            assertTrue(facade.reserve("SKU-100", 10));
        }

        @Test
        @DisplayName("Should return false when reserving zero quantity")
        void shouldReturnFalseForZeroQuantity() {
            assertFalse(facade.reserve("SKU-999", 0));
        }

        @Test
        @DisplayName("Should release stock and return true when qty remains non-negative")
        void shouldReleaseAndReturnTrue() {
            facade.reserve("SKU-200", 20);
            assertTrue(facade.release("SKU-200", 5));
        }
    }

    @Nested
    @DisplayName("Full order lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("Full scenario: reserve → place → charge should all succeed through the facade")
        void shouldExecuteFullOrderLifecycle() {
            assertTrue(facade.reserve("SKU-LAPTOP", 2));
            assertEquals("ORD-SKU-LAPTOP-2", facade.place("SKU-LAPTOP", 2));
            assertEquals("CHARGE-ORD-SKU-LAPTOP-2-1499.99", facade.charge("ORD-SKU-LAPTOP-2", 1499.99));
        }
    }
}
