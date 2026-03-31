package io.github.encapso.engine.core;

import io.github.encapso.engine.EncapsoEngine;
import io.github.encapso.engine.EncapsoType;
import java.util.*;

/**
 * Agnostic architectural boundary enforcer.
 * This is the implementation detail that Javac processor and IDE plugins share.
 */
public class DefaultEncapsoEngine implements EncapsoEngine {

    private final BoundaryRegistry registry = new BoundaryRegistry();

    @Override
    public void registerComponent(String packageName, String interfaceFqn, java.util.Set<String> allowedPublicFqns) {
        registry.register(packageName, interfaceFqn, allowedPublicFqns);
    }

    @Override
    public void registerPublicType(String packageName, String fqn) {
        registry.findMostSpecificBoundary(packageName).ifPresent(boundary -> 
            registry.register(boundary, registry.getInterface(boundary), Collections.singleton(fqn))
        );
    }

    @Override
    public java.util.Optional<Violation> checkViolation(EncapsoType referencedType, 
                                                               String callerPkg, 
                                                               String callerFqn) {
        String refFqn = referencedType.fqn();
        String refPkg = referencedType.packageName();

        // 1. Find owning boundary for type
        Optional<String> owningBoundary = registry.findMostSpecificBoundary(refPkg);
        if (owningBoundary.isEmpty() || isCommonAnnotation(refFqn)) {
            return Optional.empty();
        }

        // 2. Find owning boundary for caller
        Optional<String> callerBoundary = registry.findMostSpecificBoundary(callerPkg);

        // 3. Different boundaries -> Check for leak
        if (!owningBoundary.equals(callerBoundary)) {
            Set<String> allowed = registry.getAllowed(owningBoundary.get());
            if (allowed != null && allowed.contains(refFqn)) {
                return Optional.empty();
            }
            return Optional.of(new Violation(owningBoundary.get(), false));
        }

        // 4. Same boundary -> Check for self-reference
        String boundary = owningBoundary.get();
        String interfaceFqn = registry.getInterface(boundary);
        
        if (refFqn.equals(interfaceFqn) || (refFqn.endsWith("Builder") && registry.getAllowed(boundary).contains(refFqn))) {
            if (callerFqn.endsWith("Builder") || callerFqn.endsWith("Impl") || 
                callerFqn.endsWith("Test") || callerFqn.endsWith("IT")) {
                return Optional.empty();
            }
            return Optional.of(new Violation(boundary, true));
        }

        return Optional.empty();
    }

    private boolean isCommonAnnotation(String refFqn) {
        return refFqn.equals("io.github.encapso.Component") || 
               refFqn.equals("io.github.encapso.DelegateTo") || 
               refFqn.equals("io.github.encapso.Api");
    }
}
