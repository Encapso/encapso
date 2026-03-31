package io.github.encapso.engine;

import io.github.encapso.Component;
import java.util.Set;

/**
 * The agnostic core of Encapso. 
 * This facade decouples the architectural rules from the compiler (Javac/AP) or IDE (IntelliJ/PSI).
 */
@Component
public interface EncapsoEngine {

    /**
     * Registers a new component boundary and its allowed public API types.
     */
    void registerComponent(String packageName, String interfaceFqn, java.util.Set<String> allowedPublicFqns);

    /**
     * Registers an additional public type into the most specific existing boundary covering the package.
     */
    void registerPublicType(String packageName, String fqn);

    /**
     * Validates a type reference and reports any violations.
     * 
     * @param referencedType The type being used.
     * @param callerPkg The package of the code doing the call.
     * @param callerFqn The FQN of the class doing the call.
     * @return An Optional Violation (agnostic record).
     */
    java.util.Optional<Violation> checkViolation(EncapsoType referencedType, 
                                               String callerPkg, 
                                               String callerFqn);

    record Violation(String componentPackage, boolean isSelfReference) {}
}
