package io.github.encapso.integration.ecommerce.payments;

/**
 * Internal payment service — charge and refund operations.
 * Kept structurally distinct to validate multi-class constructor injection in the generated facade.
 */
public class PaymentService {

    private double totalCharged = 0.0;
    private double totalRefunded = 0.0;

    public String charge(String orderId, double amount) {
        totalCharged += amount;
        return "CHARGE-" + orderId + "-" + amount;
    }

    public String refund(String orderId) {
        totalRefunded += 50.0; // fixed refund for test clarity
        return "REFUND-" + orderId;
    }

    /** Internal — not exposed */
    public double getTotalCharged() { return totalCharged; }

    /** Internal — not exposed */
    public double getTotalRefunded() { return totalRefunded; }
}
