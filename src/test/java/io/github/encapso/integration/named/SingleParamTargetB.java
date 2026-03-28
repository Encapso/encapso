package io.github.encapso.integration.named;
import io.github.encapso.integration.external.ExternalDep;
public class SingleParamTargetB {
    private final ExternalDep depB;
    public SingleParamTargetB(ExternalDep depB) {
        this.depB = depB;
    }
    public String executeB() {
        return "B:" + depB.value();
    }
}
