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
     * If {@code referencedType} is an internal class being referenced illegally
     * from {@code callerPackage}, returns the component package name (for the error message).
     * Returns empty if the reference is legal.
     */
    public Optional<String> getViolatingComponentPackage(TypeElement referencedType, String callerPackage,
                                                         Elements elements) {
        String refQName = referencedType.getQualifiedName().toString();
        String refPkg = elements.getPackageOf(referencedType).getQualifiedName().toString();

        for (Map.Entry<String, Set<String>> entry : componentPackageToAllowed.entrySet()) {
            String componentPkg = entry.getKey();
            Set<String> allowed = entry.getValue();

            // Is this reference even inside this component?
            if (!isInsidePackage(refPkg, componentPkg)) continue;

            // Is the caller already inside the component? → always allowed
            if (isInsidePackage(callerPackage, componentPkg)) continue;

            // Is the type explicitly allowed (interface / builder / signature type)?
            if (allowed.contains(refQName)) continue;

            return Optional.of(componentPkg);
        }
        return Optional.empty();
    }

    public boolean isEmpty() { return componentPackageToAllowed.isEmpty(); }

    private boolean isInsidePackage(String pkg, String boundary) {
        return pkg.equals(boundary) || pkg.startsWith(boundary + ".");
    }
}
