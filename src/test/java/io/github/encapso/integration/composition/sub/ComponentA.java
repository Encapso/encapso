package io.github.encapso.integration.composition.sub;

import io.github.encapso.Component;
import io.github.encapso.DelegateTo;

@Component
public interface ComponentA {
    @DelegateTo(TargetA.class)
    String getName();

    class TargetA {
        public String getName() {
            return "ComponentA";
        }
    }
}
