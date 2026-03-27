package io.github.encapso.processor.domain;

import javax.lang.model.element.TypeElement;
import java.util.List;

/**
 * Thrown when a circular dependency is detected during topological sort.
 * Contains the list of types forming the cycle.
 */
public class CircularDependencyException extends RuntimeException {
    private final List<TypeElement> cycle;

    public CircularDependencyException(List<TypeElement> cycle) {
        super("Circular dependency detected");
        this.cycle = List.copyOf(cycle);
    }

    public List<TypeElement> getCycle() {
        return cycle;
    }
}
