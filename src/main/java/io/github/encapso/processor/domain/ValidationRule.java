package io.github.encapso.processor.domain;

/**
 * Defines a single-responsibility architectural validation rule.
 */
public interface ValidationRule {
    
    /**
     * @return true if the contextual component passes the specific rule. Outputs directly to the reporter if false.
     */
    boolean validate(ValidationContext context, Reporter reporter);

    /**
     * @return the application scope of this rule.
     */
    ValidationScope getScope();
}
