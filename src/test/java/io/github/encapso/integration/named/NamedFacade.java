package io.github.encapso.integration.named;

import io.github.encapso.Component;
import io.github.encapso.DelegateTo;

@Component
public interface NamedFacade {
    @DelegateTo(MultiParamTarget.class)
    String executeMulti();

    @DelegateTo(SingleParamTargetA.class)
    String executeA();

    @DelegateTo(SingleParamTargetB.class)
    String executeB();

    @DelegateTo(SharedParamTargetA.class)
    String executeSharedA();

    @DelegateTo(SharedParamTargetB.class)
    String executeSharedB();
}
