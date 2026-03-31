package io.github.encapso.processor.domain;

/**
 * Defines the application level of a validation rule.
 */
public enum ValidationScope {
    
    /**
     * Rules that apply to the @Component interface as a whole.
     * These run once before any method-level checks.
     */
    COMPONENT,
    
    /**
     * Rules that apply to individual delegation points (methods).
     */
    METHOD
}
