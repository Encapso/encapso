package io.github.encapso.processor.usecase;

import io.github.encapso.processor.ProcessorUtils;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import java.util.Comparator;
import java.util.List;

/**
 * Selects the optimal instantiation point (constructor or static factory) 
 * for an internal component target class.
 */
public class InstantiationPointSelector {

    private final Elements elements;

    public InstantiationPointSelector(Elements elements) {
        this.elements = elements;
    }

    /**
     * Selects the instantiation point.
     * Priorities:
     * 1. Method/Constructor annotated with @Inject
     * 2. Visible Method/Constructor with most parameters
     */
    public ExecutableElement select(TypeElement componentInterface, TypeElement type, String factoryMethodName) {
        if (factoryMethodName != null && !factoryMethodName.isEmpty()) {
            return selectFactory(componentInterface, type, factoryMethodName);
        }
        return selectConstructor(componentInterface, type);
    }

    private ExecutableElement selectFactory(TypeElement componentInterface, TypeElement type, String name) {
        List<ExecutableElement> factoryMethods = ElementFilter.methodsIn(type.getEnclosedElements()).stream()
                .filter(m -> m.getSimpleName().toString().equals(name))
                .filter(m -> m.getModifiers().contains(javax.lang.model.element.Modifier.STATIC))
                .filter(m -> ProcessorUtils.isVisible(m, componentInterface, elements))
                .toList();

        return findBest(factoryMethods);
    }

    private ExecutableElement selectConstructor(TypeElement componentInterface, TypeElement type) {
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(type.getEnclosedElements()).stream()
                .filter(c -> ProcessorUtils.isVisible(c, componentInterface, elements))
                .toList();

        return findBest(constructors);
    }

    private ExecutableElement findBest(List<ExecutableElement> candidates) {
        return candidates.stream()
                .filter(ProcessorUtils::isInjectAnnotated)
                .findFirst()
                .orElseGet(() -> candidates.stream()
                        .max(Comparator.comparingInt(m -> m.getParameters().size()))
                        .orElse(null));
    }
}
