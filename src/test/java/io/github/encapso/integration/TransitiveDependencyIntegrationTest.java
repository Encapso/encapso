package io.github.encapso.integration;

import io.github.encapso.integration.bookshop.BookshopFacade;
import io.github.encapso.integration.bookshop.BookshopFacadeBuilder;
import io.github.encapso.integration.external.DatabaseGateway;
import io.github.encapso.integration.external.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end validation of the ComponentBuilder feature.
 *
 * The Bookshop component has the following internal dependency graph:
 *
 *   [BookshopFacade]
 *       ├── RecommendationEngine
 *       │       └── BookRepository         ← internal dep
 *       │               └── DatabaseGateway ← EXTERNAL
 *       └── OrderProcessor
 *               ├── DatabaseGateway         ← EXTERNAL (deduplicated — same instance)
 *               └── EmailService            ← EXTERNAL
 *
 * Expected builder: BookshopFacadeBuilder.newBuilder()
 *                       .databaseGateway(db)   ← one setter only (deduped across both chains)
 *                       .email(email)
 *                       .build()
 */
class TransitiveDependencyIntegrationTest {

    private DatabaseGateway db;
    private EmailService email;
    private BookshopFacade facade;

    @BeforeEach
    void setUp() {
        db = new DatabaseGateway();
        email = new EmailService();

        // Only external deps are visible here. BookRepository, RecommendationEngine,
        // OrderProcessor, and BookshopFacadeImpl are all hidden inside the component.
        facade = BookshopFacadeBuilder.newBuilder()
                .db(db)
                .email(email)
                .build();
    }

    @Nested
    @DisplayName("Recommendation delegation via transitive internal chain")
    class RecommendationTests {

        @Test
        @DisplayName("recommend() should route through RecommendationEngine → BookRepository → DatabaseGateway")
        void shouldRecommendBooksViaTransitiveChain() {
            List<String> books = facade.recommend("user-1");

            // DatabaseGateway was called by BookRepository internally
            assertFalse(books.isEmpty());
            assertEquals(1, db.getQueries().size());
            assertTrue(db.getQueries().get(0).contains("books"));
        }

        @Test
        @DisplayName("Multiple recommend() calls should each reach the database")
        void shouldReachDatabaseOnEachRecommendCall() {
            facade.recommend("user-1");
            facade.recommend("user-2");

            assertEquals(2, db.getQueries().size());
        }
    }

    @Nested
    @DisplayName("Order processing with dual external deps")
    class OrderProcessingTests {

        @Test
        @DisplayName("purchase() should persist via DatabaseGateway and notify via EmailService")
        void shouldPersistAndSendNotification() {
            String orderId = facade.purchase("book-42", "user-1");

            assertNotNull(orderId);
            assertTrue(orderId.startsWith("ORDERS-"));
            assertEquals(1, db.getQueries().size());
            assertEquals(1, email.getSentEmails().size());
            assertTrue(email.getSentEmails().get(0).contains("user-1"));
            assertTrue(email.getSentEmails().get(0).contains(orderId));
        }
    }

    @Nested
    @DisplayName("Singleton behaviour — shared instance across methods")
    class SingletonTests {

        @Test
        @DisplayName("DatabaseGateway should be the same instance for both recommend() and purchase()")
        void shouldUseSameDatabaseGatewayInstanceAcrossBothTCs() {
            // Calls through two different TCs that both use DatabaseGateway internally
            facade.recommend("user-1");    // → BookRepository → db
            facade.purchase("book-1", "user-1"); // → OrderProcessor → db

            // Both queries hit the SAME DatabaseGateway instance
            assertEquals(2, db.getQueries().size());
        }

        @Test
        @DisplayName("EmailService should only be used by OrderProcessor, not by recommend()")
        void shouldNotLeakEmailServiceToRecommendationEngine() {
            facade.recommend("user-1");

            // recommend() has no path to EmailService
            assertTrue(email.getSentEmails().isEmpty());
        }
    }

    @Nested
    @DisplayName("Builder API — no internal types exposed")
    class BuilderApiTests {

        @Test
        @DisplayName("newBuilder() should return a fluent builder that produces a valid facade")
        void shouldProduceValidFacadeFromBuilder() {
            BookshopFacade result = BookshopFacadeBuilder.newBuilder()
                    .db(new DatabaseGateway())
                    .email(new EmailService())
                    .build();

            // build() returns the interface, not the Impl — correct type
            assertInstanceOf(BookshopFacade.class, result);
        }

        @Test
        @DisplayName("Builder should produce independent facade instances with isolated state")
        void shouldProduceIsolatedFacadeInstances() {
            DatabaseGateway db1 = new DatabaseGateway();
            DatabaseGateway db2 = new DatabaseGateway();

            BookshopFacade facade1 = BookshopFacadeBuilder.newBuilder()
                    .db(db1).email(new EmailService()).build();
            BookshopFacade facade2 = BookshopFacadeBuilder.newBuilder()
                    .db(db2).email(new EmailService()).build();

            facade1.recommend("user-A");
            facade2.recommend("user-B");
            facade2.recommend("user-C");

            assertEquals(1, db1.getQueries().size());
            assertEquals(2, db2.getQueries().size());
        }
    }
}
