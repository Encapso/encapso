package io.github.encapso.integration.bookshop;

import io.github.encapso.integration.external.DatabaseGateway;
import io.github.encapso.integration.external.EmailService;

/**
 * Internal class — depends on TWO external deps: DatabaseGateway and EmailService.
 * This validates that:
 * 1. Multiple external deps are all surfaced to the builder.
 * 2. DatabaseGateway is deduplicated (shared with BookRepository chain) — ONE builder setter.
 */
public class OrderProcessor {

    private final DatabaseGateway db;
    private final EmailService email;

    public OrderProcessor(DatabaseGateway db, EmailService email) {
        this.db = db;
        this.email = email;
    }

    public String purchase(String bookId, String userId) {
        String orderId = db.save("orders", bookId + ":" + userId);
        email.send(userId, "Order confirmed: " + orderId);
        return orderId;
    }
}
