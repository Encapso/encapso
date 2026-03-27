package io.github.encapso.integration.external;

import java.util.ArrayList;
import java.util.List;

/** Simulates an external database gateway. Tracks all queries for test verification. */
public class DatabaseGateway {

    private final List<String> queries = new ArrayList<>();

    public List<String> findAll(String table) {
        queries.add("SELECT * FROM " + table);
        return List.of("item-1", "item-2");
    }

    public String save(String table, String data) {
        String id = table.toUpperCase() + "-" + (queries.size() + 1);
        queries.add("INSERT INTO " + table + ": " + data);
        return id;
    }

    public List<String> getQueries() { return queries; }
}
