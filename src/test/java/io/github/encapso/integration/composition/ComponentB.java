package io.github.encapso.integration.composition;

import io.github.encapso.Component;
import io.github.encapso.DelegateTo;
import io.github.encapso.integration.composition.sub.ComponentA;

@Component
public interface ComponentB {
    @DelegateTo(TargetB.class)
    String getNestedName();

    class TargetB {
        private final ComponentA compA;

        public TargetB(ComponentA compA) {
            this.compA = compA;
        }

        public String getNestedName() {
            return "Nested: " + compA.getName();
        }
    }
}
