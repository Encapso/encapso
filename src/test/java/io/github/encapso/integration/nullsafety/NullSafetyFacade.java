package io.github.encapso.integration.nullsafety;

import io.github.encapso.Component;
import io.github.encapso.DelegateTo;

@Component
public interface NullSafetyFacade {
    @DelegateTo(NullSafetyTarget.class)
    void process();
}
