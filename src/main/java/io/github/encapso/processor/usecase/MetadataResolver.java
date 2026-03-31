package io.github.encapso.processor.usecase;

import io.github.encapso.processor.ProcessorUtils;
import io.github.encapso.processor.domain.Reporter;
import io.github.encapso.processor.domain.ValidationContext;
import io.github.encapso.processor.domain.ValidationRule;
import io.github.encapso.processor.domain.ValidationScope;

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

    private final List<ValidationRule> componentRules;
    private final List<ValidationRule> methodRules;
    private final Elements elements;
    private final Types types;

    public MetadataResolver(List<ValidationRule> rules, Elements elements, Types types) {
        this.componentRules = rules.stream()
                .filter(r -> r.getScope() == ValidationScope.COMPONENT)
                .toList();
        this.methodRules = rules.stream()
                .filter(r -> r.getScope() == ValidationScope.METHOD)
                .toList();
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
        ValidationContext initialCtx = new ValidationContext(interfaceElement, null, null, null, elements, types, roundEnv);
        if (!runRules(componentRules, initialCtx, reporter)) {
            isValid = false;
        }

        // 2. Method-level Validation
        for (Element enclosed : interfaceElement.getEnclosedElements()) {
            if (enclosed instanceof ExecutableElement method) {
                Optional<ProcessorUtils.DelegateRequest> request = ProcessorUtils.getDelegateRequest(method, types);
                if (request.isEmpty()) continue;

                TypeElement target = request.get().target();
                String factoryMethodName = request.get().factoryMethod();
                ValidationContext ctx = new ValidationContext(interfaceElement, method, target, factoryMethodName, elements, types, roundEnv);

                if (runRules(methodRules, ctx, reporter)) {
                    delegateMapping.put(method, target);
                    targetToFactory.put(target, factoryMethodName);
                    uniqueTargetClasses.add(target);
                } else {
                    isValid = false;
                }
            }
        }
        
        if (isValid && delegateMapping.isEmpty()) {
            reporter.error("@Component must have at least one method annotated with @DelegateTo", interfaceElement);
            isValid = false;
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
