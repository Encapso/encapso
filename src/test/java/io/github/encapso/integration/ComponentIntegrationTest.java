package io.github.encapso.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComponentIntegrationTest {

    @Test
    void shouldInstantiateAndExecuteFacadeViaBuilder() {
        // MyTarget has no external deps — builder requires no setters
        MyFacade facade = MyFacadeBuilder.newBuilder().build();

        int result = facade.execute(10);

        assertEquals(30, result);
    }
}
