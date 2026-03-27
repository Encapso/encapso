package io.github.encapso;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type as part of the component's public API.
 * Even if it doesn't appear in the Facade's method signatures,
 * it will be visible to external consumers.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Api {
}
