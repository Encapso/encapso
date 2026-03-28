package io.github.encapso.integration.nullsafety;

import io.github.encapso.integration.external.DepA;
import io.github.encapso.integration.external.DepB;
import io.github.encapso.integration.external.DepC;
import io.github.encapso.integration.external.DepD;
import javax.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;
import com.example.NonNull;

public class NullSafetyTarget {
    public NullSafetyTarget(
        @Nonnull DepA depA,
        @NotNull DepB depB,
        @NonNull DepC depC,
        DepD depD // Optional
    ) {}

    public void process() {}
}
