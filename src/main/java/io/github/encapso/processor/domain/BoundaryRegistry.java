package io.github.encapso.processor.domain;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.*;

/**
 * Tracks the "allowed" public types for each component package boundary.
 *
 * For each {@code @Component} interface, the following types are allowed externally:
 * <ol>
 *   <li>The {@code @Component} interface itself</li>
 *   <li>The generated {@code *Builder} class</li>
 *   <li>All types appearing in the interface method signatures (params, returns, exceptions)</li>
 * </ol>
 *
 * Every other type in the component package tree is considered internal and
 * cannot be referenced directly from outside code.
 */
public class BoundaryRegistry {

    /** component package → set of allowed fully-qualified type names */
    private final Map<String, Set<String>> componentPackageToAllowed = new LinkedHashMap<>();

    public void register(String componentPackage, Set<String> allowedNames) {
        componentPackageToAllowed.merge(componentPackage, new LinkedHashSet<>(allowedNames),
                (existing, incoming) -> { existing.addAll(incoming); return existing; });
    }

    /**
     * Finds the "owning component boundary" for a given package.
     * The owning boundary is the component with the longest matching package prefix.
     */
    private Optional<String> findMostSpecificBoundary(String subPkg) {
        return componentPackageToAllowed.keySet().stream()
                .filter(boundary -> isInsidePackage(subPkg, boundary))
                .max(Comparator.comparingInt(String::length));
    }

    /**
     * If {@code referencedType} is an internal class being referenced illegally
     * from {@code callerPackage}, returns the component package name (for the error message).
     * Returns empty if the reference is legal.
     */
    public Optional<String> getViolatingComponentPackage(TypeElement referencedType, String callerPackage,
                                                         Elements elements) {
        String refPkg = elements.getPackageOf(referencedType).getQualifiedName().toString();
        
        // 1. Find the deepest boundary that owns this type
        Optional<String> owningBoundary = findMostSpecificBoundary(refPkg);
        if (owningBoundary.isEmpty()) return Optional.empty();

        // 2. Find the deepest boundary that owns the caller
        Optional<String> callerBoundary = findMostSpecificBoundary(callerPackage);

        // 3. If they are in different boundaries, check for violations
        if (!owningBoundary.equals(callerBoundary)) {
            String refQName = referencedType.getQualifiedName().toString();
            Set<String> allowed = componentPackageToAllowed.get(owningBoundary.get());

            // Is the type explicitly allowed (interface / builder / signature type)?
            if (allowed != null && allowed.contains(refQName)) {
                return Optional.empty();
            }

            return owningBoundary;
        }

        return Optional.empty();
    }

    public boolean isEmpty() { return componentPackageToAllowed.isEmpty(); }

    private boolean isInsidePackage(String pkg, String boundary) {
        return pkg.equals(boundary) || pkg.startsWith(boundary + ".");
    }
}
