package io.github.encapso.integration.ecommerce.inventory;

/**
 * Internal inventory service — validates boolean return type routing through the generated facade.
 */
public class InventoryService {

    private int reservedQty = 0;

    /** Overload 1: reserve with quantity */
    public boolean reserve(String sku, int qty) {
        if (qty <= 0) return false;
        reservedQty += qty;
        return true;
    }

    public boolean release(String sku, int qty) {
        reservedQty -= qty;
        return reservedQty >= 0;
    }

    /** Internal — not exposed */
    public int getReservedQty() { return reservedQty; }
}
