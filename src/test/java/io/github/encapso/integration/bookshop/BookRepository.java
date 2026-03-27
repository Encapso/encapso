package io.github.encapso.integration.bookshop;

import io.github.encapso.integration.external.DatabaseGateway;

import java.util.List;

/**
 * Internal class — depends on an EXTERNAL DatabaseGateway.
 * The DependencyAnalyzer must surface DatabaseGateway as a builder parameter.
 */
public class BookRepository {

    private final DatabaseGateway db;

    public BookRepository(DatabaseGateway db) {
        this.db = db;
    }

    public List<String> findAll() {
        return db.findAll("books");
    }
}
