package io.github.encapso.engine;

import io.github.encapso.Api;

/**
 * Agnostic representation of a Java type.
 */
@Api
public record EncapsoType(String fqn, String packageName) {
    public String simpleName() {
        return fqn.contains(".") ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn;
    }
}
