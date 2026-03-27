package io.github.encapso.integration.bookshop;

import io.github.encapso.Component;
import io.github.encapso.DelegateTo;

import java.util.List;

/**
 * Bookshop component facade — exercises the full dependency analysis pipeline:
 *
 * <ul>
 *   <li>RecommendationEngine depends on BookRepository (internal) which depends on DatabaseGateway (external)</li>
 *   <li>OrderProcessor depends on DatabaseGateway (external, shared) and EmailService (external)</li>
 * </ul>
 *
 * Expected generated builder: {@code BookshopFacadeBuilder.newBuilder()
 *     .databaseGateway(db)
 *     .emailService(email)
 *     .build()}
 */
@Component
public interface BookshopFacade {

    @DelegateTo(RecommendationEngine.class)
    List<String> recommend(String userId);

    @DelegateTo(OrderProcessor.class)
    String purchase(String bookId, String userId);
}
