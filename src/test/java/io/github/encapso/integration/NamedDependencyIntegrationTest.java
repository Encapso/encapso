package io.github.encapso.integration;

import io.github.encapso.integration.external.ExternalDep;
import io.github.encapso.integration.named.NamedFacade;
import io.github.encapso.integration.named.NamedFacadeBuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NamedDependencyIntegrationTest {

    @Test
    void shouldHandleMultipleParametersOfSameType() {
        NamedFacade facade = NamedFacadeBuilder.newBuilder()
                .dep1(new ExternalDep("v1"))
                .dep2(new ExternalDep("v2"))
                .depA(new ExternalDep("A"))
                .depB(new ExternalDep("B"))
                .build();

        assertEquals("v1-v2", facade.executeMulti());
    }

    @Test
    void shouldHandleNamedDependenciesAcrossDifferentClasses() {
        NamedFacade facade = NamedFacadeBuilder.newBuilder()
                .dep1(new ExternalDep("v1"))
                .dep2(new ExternalDep("v2"))
                .depA(new ExternalDep("ValA"))
                .depB(new ExternalDep("ValB"))
                .build();

        assertEquals("A:ValA", facade.executeA());
        assertEquals("B:ValB", facade.executeB());
    }

    @Test
    void shouldShareDependencyWhenNamesAreEqual() {
        ExternalDep sharedVal = new ExternalDep("SharedValue");
        NamedFacade facade = NamedFacadeBuilder.newBuilder()
                .dep1(new ExternalDep("v1"))
                .dep2(new ExternalDep("v2"))
                .depA(new ExternalDep("A"))
                .depB(new ExternalDep("B"))
                .shared(sharedVal)
                .build();

        assertEquals("SA:SharedValue", facade.executeSharedA());
        assertEquals("SB:SharedValue", facade.executeSharedB());
    }
}
