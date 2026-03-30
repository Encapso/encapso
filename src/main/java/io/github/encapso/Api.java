package io.github.encapso;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type (interface, class, record, or enum) as part of the public API of
 * an Encapso Component.
 * Types annotated with {@code @Api} are visible outside the component's
 * package,
 * even if the package is configured as "content private".
 * 
 * <p>
 * Requirements:
 * <ul>
 * <li>The annotated type must be {@code public}.</li>
 * <li>The annotated type must be inside a package managed by a
 * {@link Component}.</li>
 * </ul>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Api {
}
