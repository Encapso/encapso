package io.github.encapso.processor.usecase;

import io.github.encapso.processor.ProcessorUtils;
import io.github.encapso.processor.domain.Reporter;
import io.github.encapso.processor.domain.ValidationContext;
import io.github.encapso.processor.domain.ValidationRule;
import io.github.encapso.processor.validation.ComponentInterfaceHierarchyRule;
import io.github.encapso.processor.validation.SingleComponentPerPackageRule;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;

/**
 * Resolves the metadata required to process a @Component.
 * Performs initial validation and extracts delegate-to mappings.
 */
public class MetadataResolver {

    private final List<ValidationRule> criticalRules;
    private final List<ValidationRule> signatureRules;
    private final Elements elements;
    private final Types types;

    public MetadataResolver(List<ValidationRule> criticalRules,
                            List<ValidationRule> signatureRules,
                            Elements elements,
                            Types types) {
        this.criticalRules = criticalRules;
        this.signatureRules = signatureRules;
        this.elements = elements;
        this.types = types;
    }

    public record ComponentMetadata(
            boolean isValid,
            Map<ExecutableElement, TypeElement> delegateMapping,
            Map<TypeElement, String> targetToFactory,
            Set<TypeElement> targetClasses) {
    }

    public ComponentMetadata resolve(TypeElement interfaceElement, RoundEnvironment roundEnv, Reporter reporter) {
        Map<ExecutableElement, TypeElement> delegateMapping = new LinkedHashMap<>();
        Map<TypeElement, String> targetToFactory = new LinkedHashMap<>();
        Set<TypeElement> uniqueTargetClasses = new LinkedHashSet<>();
        boolean isValid = true;

        // 1. Initial Interface Validation (Component-level rules)
        ValidationContext initialCtx = new ValidationContext(interfaceElement, null, null, elements, types, roundEnv);
        if (!runRules(criticalRules.stream()
                .filter(r -> r instanceof ComponentInterfaceHierarchyRule || r instanceof SingleComponentPerPackageRule)
                .toList(), initialCtx, reporter)) {
            isValid = false;
        }

        // 2. Method-level Validation
        for (Element enclosed : interfaceElement.getEnclosedElements()) {
            if (enclosed instanceof ExecutableElement method) {
                Optional<ProcessorUtils.DelegateRequest> request = ProcessorUtils.getDelegateRequest(method, types);
                if (request.isEmpty()) continue;

                TypeElement target = request.get().target();
                ValidationContext ctx = new ValidationContext(interfaceElement, method, target, elements, types, roundEnv);

                // Run critical rules that are NOT component-level
                if (!runRules(criticalRules.stream()
                        .filter(r -> !(r instanceof ComponentInterfaceHierarchyRule))
                        .toList(), ctx, reporter)) {
                    isValid = false;
                    continue;
                }

                if (runRules(signatureRules, ctx, reporter)) {
                    delegateMapping.put(method, target);
                    targetToFactory.put(target, request.get().factoryMethod());
                    uniqueTargetClasses.add(target);
                } else {
                    isValid = false;
                }
            }
        }
        return new ComponentMetadata(isValid, delegateMapping, targetToFactory, uniqueTargetClasses);
    }

    private boolean runRules(List<ValidationRule> rules, ValidationContext ctx, Reporter reporter) {
        boolean valid = true;
        for (ValidationRule rule : rules) {
            if (!rule.validate(ctx, reporter)) {
                valid = false;
            }
        }
        return valid;
    }
}
