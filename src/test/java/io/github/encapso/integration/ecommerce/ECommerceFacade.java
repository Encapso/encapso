package io.github.encapso.integration.ecommerce;

import io.github.encapso.Component;
import io.github.encapso.DelegateTo;
import io.github.encapso.integration.ecommerce.inventory.InventoryService;
import io.github.encapso.integration.ecommerce.orders.OrderService;
import io.github.encapso.integration.ecommerce.payments.PaymentService;

/**
 * Multi-component facade binding three distinct subpackage services.
 *
 * Validates:
 * - Methods delegated to different classes in different subpackages
 * - Correct overload resolution: place(String) vs place(String, int)
 * - Mixed return types: String, boolean
 * - Internal service methods are not present in the facade
 */
@Component
public interface ECommerceFacade {

    // --- OrderService bindings ---

    /**
     * Delegates to the single-unit overload: OrderService.place(String).
     * Validates that the processor correctly dispatches to this overload
     * without confusing it with place(String, int).
     */
    @DelegateTo(OrderService.class)
    String place(String sku);

    /**
     * Delegates to the quantity overload: OrderService.place(String, int).
     * Validates we can expose both overloads through a single component.
     */
    @DelegateTo(OrderService.class)
    String place(String sku, int qty);

    /** Delegates to: OrderService.cancel(String) */
    @DelegateTo(OrderService.class)
    boolean cancel(String orderId);

    // --- PaymentService bindings ---

    /** Delegates to: PaymentService.charge(String, double) */
    @DelegateTo(PaymentService.class)
    String charge(String orderId, double amount);

    /** Delegates to: PaymentService.refund(String) */
    @DelegateTo(PaymentService.class)
    String refund(String orderId);

    // --- InventoryService bindings ---

    /** Delegates to: InventoryService.reserve(String, int) — returns boolean */
    @DelegateTo(InventoryService.class)
    boolean reserve(String sku, int qty);

    /** Delegates to: InventoryService.release(String, int) — returns boolean */
    @DelegateTo(InventoryService.class)
    boolean release(String sku, int qty);
}
