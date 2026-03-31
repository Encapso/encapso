package io.github.encapso.engine;

import io.github.encapso.Api;
import io.github.encapso.engine.core.DefaultEncapsoEngine;

/**
 * Entry point to create the agnostic Encapso engine.
 */
@Api
public final class EncapsoEngineProvider {
    private EncapsoEngineProvider() {}

    public static EncapsoEngine create() {
        return new DefaultEncapsoEngine();
    }
}
