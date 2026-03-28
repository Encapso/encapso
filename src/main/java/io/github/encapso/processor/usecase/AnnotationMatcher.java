package io.github.encapso.processor.usecase;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import java.util.Set;

/**
 * Utility for matching annotations by name, supporting "meta-annotation" style
 * matching where the specific library (javax, jetbrains, lombok) doesn't matter.
 */
public class AnnotationMatcher {

    private static final Set<String> NON_NULL_NAMES = Set.of("nonnull", "notnull");

    public boolean isNonNull(Element element) {
        return element.getAnnotationMirrors().stream()
                .map(this::getAnnotationSimpleName)
                .anyMatch(NON_NULL_NAMES::contains);
    }

    private String getAnnotationSimpleName(AnnotationMirror mirror) {
        return mirror.getAnnotationType().asElement().getSimpleName().toString().toLowerCase();
    }
}
