package io.github.encapso.integration.composition;

import io.github.encapso.integration.composition.sub.ComponentA;
import io.github.encapso.integration.composition.sub.ComponentABuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

/**
 * Verifies that a @Component can be used as a dependency for another @Component
 * while residing in different packages.
 */
class ComponentCompositionIntegrationTest {

    @Test
    @DisplayName("Should support component composition across packages")
    void shouldSupportComposition() {
        // First, build the inner component
        ComponentA compA = ComponentABuilder.newBuilder()
                .build();

        // Then, pass it as a dependency to the outer component's builder
        ComponentB compB = ComponentBBuilder.newBuilder()
                .compA(compA)
                .build();

        assertThat(compB.getNestedName()).isEqualTo("Nested: ComponentA");
    }
}
