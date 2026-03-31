package io.github.encapso.processor.usecase;

import io.github.encapso.processor.ProcessorUtils;
import java.util.HashSet;
import java.util.Set;

/**
 * Strategy for allocating unique, collision-free instance names for generated code.
 */
public class InstanceNamingStrategy {

    private final Set<String> usedNames = new HashSet<>();

    /**
     * Reserves a name and ensures it is unique.
     * @param requestedName the suggested name
     * @return a unique version of the suggested name
     */
    public String allocate(String requestedName) {
        String base = ProcessorUtils.camelCase(requestedName);
        String current = base;
        int counter = 1;
        while (usedNames.contains(current)) {
            current = base + (++counter);
        }
        usedNames.add(current);
        return current;
    }

    /**
     * Pre-reserves a set of names (e.g. from external dependencies).
     */
    public void reserve(Iterable<String> names) {
        for (String name : names) {
            usedNames.add(name);
        }
    }
}
