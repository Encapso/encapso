package io.github.encapso.integration.bookshop;

import java.util.List;

/**
 * Internal class — depends on BookRepository (INTERNAL).
 * DependencyAnalyzer must: instantiate BookRepository first, then RecommendationEngine.
 * DatabaseGateway (BookRepository's dep) must still be surfaced to the builder.
 */
public class RecommendationEngine {

    private final BookRepository bookRepository;

    public RecommendationEngine(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public List<String> recommend(String userId) {
        return bookRepository.findAll();
    }
}
