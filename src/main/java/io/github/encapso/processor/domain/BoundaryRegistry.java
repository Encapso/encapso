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
    /** component package → fully-qualified component interface name */
    private final Map<String, String> componentPackageToInterface = new LinkedHashMap<>();

    public void register(String componentPackage, String interfaceFqn, Set<String> allowedNames) {
        componentPackageToInterface.put(componentPackage, interfaceFqn);
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
     * from {@code callerPackage}, returns a {@link Violation} (for the error message).
     * Returns empty if the reference is legal.
     */
    public Optional<Violation> getViolatingComponentPackage(TypeElement referencedType, String callerPackage,
                                                          String callerFqn, Elements elements) {
        String refQName = referencedType.getQualifiedName().toString();
        return getViolatingComponentPackageForFqn(refQName, callerPackage, callerFqn, elements);
    }

    public Optional<Violation> getViolatingComponentPackageForFqn(String refQName, String callerPackage,
                                                                   String callerFqn, Elements elements) {
        String refPkg = refQName.contains(".") ? refQName.substring(0, refQName.lastIndexOf('.')) : "";
        
        // 1. Find the deepest boundary that owns this type
        Optional<String> owningBoundary = findMostSpecificBoundary(refPkg);
        if (owningBoundary.isEmpty() || 
            refQName.equals("io.github.encapso.Component") || 
            refQName.equals("io.github.encapso.DelegateTo") || 
            refQName.equals("io.github.encapso.Api")) {
            return Optional.empty();
        }

        // 2. Find the deepest boundary that owns the caller
        Optional<String> callerBoundary = findMostSpecificBoundary(callerPackage);

        // 3. If they are in different boundaries, check for violations
        if (!owningBoundary.equals(callerBoundary)) {
            Set<String> allowed = componentPackageToAllowed.get(owningBoundary.get());

            // Is the type explicitly allowed (interface / builder / signature type)?
            if (allowed != null && allowed.contains(refQName)) {
                return Optional.empty();
            }

            return owningBoundary.map(pkg -> new Violation(pkg, false));
        }

        // 4. Restriction: Internal classes cannot refer to their own component interface
        // Exception: The generated Builder (and the Facade implementation) are allowed.
        String boundary = owningBoundary.get();
        String interfaceFqn = componentPackageToInterface.get(boundary);
        boolean isInterfaceRef = refQName.equals(interfaceFqn);
        boolean isBuilderRef = refQName.endsWith("Builder") && componentPackageToAllowed.get(boundary).contains(refQName);

        if (isInterfaceRef || isBuilderRef) {
            // Exceptions: Builders, Impls and Tests are allowed to refer to the interface.
            if (callerFqn.endsWith("Builder") || callerFqn.endsWith("Impl") || 
                callerFqn.endsWith("Test") || callerFqn.endsWith("IT")) {
                return Optional.empty();
            }
            return Optional.of(new Violation(boundary, true));
        }

        return Optional.empty();
    }

    public record Violation(String componentPackage, boolean isSelfReference) {}

    public void registerPublicType(String pkg, String typeName) {
        findMostSpecificBoundary(pkg).ifPresent(boundary -> 
            componentPackageToAllowed.computeIfAbsent(boundary, k -> new HashSet<>()).add(typeName)
        );
    }

    public boolean isPublic(String typeName) {
        return componentPackageToAllowed.values().stream().anyMatch(set -> set.contains(typeName));
    }

    public boolean isEmpty() { return componentPackageToAllowed.isEmpty(); }

    public int getBoundariesCount() { return componentPackageToAllowed.size(); }

    private boolean isInsidePackage(String pkg, String boundary) {
        return pkg.equals(boundary) || pkg.startsWith(boundary + ".");
    }
}
