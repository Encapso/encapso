package io.github.encapso.integration.ecommerce.orders;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal order service — intentionally has multiple overloads and unrelated methods
 * that should NOT interfere with the facade's method resolution.
 */
public class OrderService {

    private final List<String> placedOrders = new ArrayList<>();
    private final List<String> cancelledOrders = new ArrayList<>();

    /** Overload 1: place a single unit */
    public String place(String sku) {
        String orderId = "ORD-" + sku + "-1";
        placedOrders.add(orderId);
        return orderId;
    }

    /** Overload 2: place a specific quantity — the facade should bind to THIS overload for qty variant */
    public String place(String sku, int qty) {
        String orderId = "ORD-" + sku + "-" + qty;
        placedOrders.add(orderId);
        return orderId;
    }

    public boolean cancel(String orderId) {
        cancelledOrders.add(orderId);
        return true;
    }

    /** Extra internal method — must NOT be exposed through the facade */
    public List<String> getPlacedOrders() {
        return placedOrders;
    }

    /** Extra internal method — must NOT be exposed through the facade */
    public List<String> getCancelledOrders() {
        return cancelledOrders;
    }
}
