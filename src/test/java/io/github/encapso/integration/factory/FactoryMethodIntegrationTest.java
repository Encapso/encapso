package io.github.encapso.integration.factory;

import io.github.encapso.Component;
import io.github.encapso.DelegateTo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

import io.github.encapso.integration.external.ExternalDep;

class FactoryMethodIntegrationTest {

    public static class FactoryTarget {
        private final java.util.List<String> data;

        private FactoryTarget(java.util.List<String> data) {
            this.data = data;
        }

        public static FactoryTarget create(java.util.List<String> data) {
            return new FactoryTarget(data);
        }

        public String getResult() {
            return String.join(",", data);
        }
    }

    @Component
    public interface FactoryComponent {
        @DelegateTo(value = FactoryTarget.class, factoryMethod = "create")
        String getResult();
    }

    @Test
    @DisplayName("Should instantiate target class via static factory method")
    void shouldSupportFactoryMethod() {
        java.util.List<String> data = java.util.List.of("a", "b");
        
        FactoryComponent component = FactoryComponentBuilder.newBuilder()
                .data(data)
                .build();

        assertThat(component.getResult()).isEqualTo("a,b");
    }
}
