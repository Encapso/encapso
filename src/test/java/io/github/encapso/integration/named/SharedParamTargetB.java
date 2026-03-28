package io.github.encapso.integration.named;
import io.github.encapso.integration.external.ExternalDep;
public class SharedParamTargetB {
    private final ExternalDep shared;
    public SharedParamTargetB(ExternalDep shared) {
        this.shared = shared;
    }
    public String executeSharedB() {
        return "SB:" + shared.value();
    }
}
