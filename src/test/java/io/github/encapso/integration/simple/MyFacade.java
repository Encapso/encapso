package io.github.encapso.integration.simple;

import io.github.encapso.Component;
import io.github.encapso.DelegateTo;

@Component
public interface MyFacade {
    @DelegateTo(MyTarget.class)
    int execute(int input);
}
