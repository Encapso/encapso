package io.github.encapso.integration;

import io.github.encapso.integration.nullsafety.NullSafetyFacade;
import io.github.encapso.integration.nullsafety.NullSafetyFacadeBuilder;
import io.github.encapso.integration.external.DepA;
import io.github.encapso.integration.external.DepB;
import io.github.encapso.integration.external.DepC;
import io.github.encapso.integration.external.DepD;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NullSafetyRuntimeIntegrationTest {

    @Test
    @DisplayName("Setter should throw NPE for @Nonnull (javax)")
    void shouldThrowOnNullSetterNonnull() {
        NullSafetyFacadeBuilder builder = NullSafetyFacadeBuilder.newBuilder();
        NullPointerException ex = assertThrows(NullPointerException.class, () -> builder.depA(null));
        assertEquals("'depA' must not be null", ex.getMessage());
    }

    @Test
    @DisplayName("Setter should throw NPE for @NotNull (jetbrains)")
    void shouldThrowOnNullSetterNotNull() {
        NullSafetyFacadeBuilder builder = NullSafetyFacadeBuilder.newBuilder();
        NullPointerException ex = assertThrows(NullPointerException.class, () -> builder.depB(null));
        assertEquals("'depB' must not be null", ex.getMessage());
    }

    @Test
    @DisplayName("Setter should throw NPE for @NonNull (custom)")
    void shouldThrowOnNullSetterCustomNonNull() {
        NullSafetyFacadeBuilder builder = NullSafetyFacadeBuilder.newBuilder();
        NullPointerException ex = assertThrows(NullPointerException.class, () -> builder.depC(null));
        assertEquals("'depC' must not be null", ex.getMessage());
    }

    @Test
    @DisplayName("build() should throw NPE if a required dependency is missing")
    void shouldThrowOnMissingRequiredDep() {
        NullSafetyFacadeBuilder builder = NullSafetyFacadeBuilder.newBuilder()
                .depA(new DepA())
                .depB(new DepB());
                // depC is missing

        NullPointerException ex = assertThrows(NullPointerException.class, builder::build);
        assertEquals("Required dependency 'depC' was not provided to the builder", ex.getMessage());
    }

    @Test
    @DisplayName("Optional dependencies should allow null and missing values")
    void shouldAllowNullForOptional() {
        NullSafetyFacade facade = NullSafetyFacadeBuilder.newBuilder()
                .depA(new DepA())
                .depB(new DepB())
                .depC(new DepC())
                .depD(null) // explicitly null
                .build();
        
        assertNotNull(facade);
        
        NullSafetyFacade facade2 = NullSafetyFacadeBuilder.newBuilder()
                .depA(new DepA())
                .depB(new DepB())
                .depC(new DepC())
                // depD is omitted
                .build();
        
        assertNotNull(facade2);
    }
}
