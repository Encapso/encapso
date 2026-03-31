package io.github.encapso.engine.core;

import io.github.encapso.engine.EncapsoType;
import java.util.*;

/**
 * Agnostic storage for architectural boundaries.
 */
public class BoundaryRegistry {
    private final Map<String, Set<String>> componentPackageToAllowed = new LinkedHashMap<>();
    private final Map<String, String> componentPackageToInterface = new LinkedHashMap<>();

    public void register(String componentPackage, String interfaceFqn, Set<String> allowedNames) {
        componentPackageToInterface.put(componentPackage, interfaceFqn);
        componentPackageToAllowed.merge(componentPackage, new LinkedHashSet<>(allowedNames),
                (existing, incoming) -> { existing.addAll(incoming); return existing; });
    }

    public Optional<String> findMostSpecificBoundary(String subPkg) {
        return componentPackageToAllowed.keySet().stream()
                .filter(boundary -> isInsidePackage(subPkg, boundary))
                .max(Comparator.comparingInt(String::length));
    }

    public boolean isInsidePackage(String pkg, String boundary) {
        return pkg.equals(boundary) || pkg.startsWith(boundary + ".");
    }

    public Set<String> getAllowed(String boundary) {
        return componentPackageToAllowed.get(boundary);
    }

    public String getInterface(String boundary) {
        return componentPackageToInterface.get(boundary);
    }

    public boolean isEmpty() { return componentPackageToAllowed.isEmpty(); }
}
