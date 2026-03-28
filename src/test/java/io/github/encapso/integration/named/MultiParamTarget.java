package io.github.encapso.integration.named;
import io.github.encapso.integration.external.ExternalDep;
public class MultiParamTarget {
    private final ExternalDep dep1;
    private final ExternalDep dep2;
    public MultiParamTarget(ExternalDep dep1, ExternalDep dep2) {
        this.dep1 = dep1;
        this.dep2 = dep2;
    }
    public String executeMulti() {
        return dep1.value() + "-" + dep2.value();
    }
}
