package io.github.encapso.integration.named;
import io.github.encapso.integration.external.ExternalDep;
public class SharedParamTargetA {
    private final ExternalDep shared;
    public SharedParamTargetA(ExternalDep shared) {
        this.shared = shared;
    }
    public String executeSharedA() {
        return "SA:" + shared.value();
    }
}
