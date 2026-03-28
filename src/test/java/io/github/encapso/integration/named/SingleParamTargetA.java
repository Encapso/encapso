package io.github.encapso.integration.named;
import io.github.encapso.integration.external.ExternalDep;
public class SingleParamTargetA {
    private final ExternalDep depA;
    public SingleParamTargetA(ExternalDep depA) {
        this.depA = depA;
    }
    public String executeA() {
        return "A:" + depA.value();
    }
}
