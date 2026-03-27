package io.github.encapso.processor.usecase;

import io.github.encapso.processor.ProcessorUtils;
import io.github.encapso.processor.domain.FacadeGenerator;
import io.github.encapso.processor.domain.Reporter;
import io.github.encapso.processor.domain.ValidationContext;
import io.github.encapso.processor.domain.ValidationRule;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Clean Architecture Application Orchestrator. 
 * Invokes stateless validation adapters and routes to code generation if strictly successful.
 */
public class ComponentProcessorUseCase {

    private final List<ValidationRule> criticalRules;
    private final List<ValidationRule> signatureRules;
    private final FacadeGenerator generator;
    private final Reporter reporter;
    private final ProcessingEnvironment env;

    public ComponentProcessorUseCase(List<ValidationRule> criticalRules, 
                                     List<ValidationRule> signatureRules,
                                     FacadeGenerator generator,
                                     Reporter reporter,
                                     ProcessingEnvironment env) {
        this.criticalRules = criticalRules;
        this.signatureRules = signatureRules;
        this.generator = generator;
        this.reporter = reporter;
        this.env = env;
    }

    public void processComponent(TypeElement interfaceElement) {
        boolean isComponentValid = true;
        Map<ExecutableElement, TypeElement> delegateMapping = new LinkedHashMap<>();

        for (Element enclosed : interfaceElement.getEnclosedElements()) {
            if (enclosed instanceof ExecutableElement methodElement) {
                Optional<TypeElement> optTarget = ProcessorUtils.getDelegateTargetElement(methodElement, env);
                if (optTarget.isPresent()) {
                    TypeElement targetElement = optTarget.get();
                    ValidationContext ctx = new ValidationContext(interfaceElement, methodElement, targetElement, env);

                    // 1. Base Structural Rules (Package visibility)
                    boolean passedCritical = runRules(criticalRules, ctx);
                    
                    if (!passedCritical) {
                        isComponentValid = false;
                        continue; // Stop enforcing granular parameters if the class itself isn't structurally visible
                    }

                    // 2. Contract Signature Rules
                    boolean methodValid = runRules(signatureRules, ctx);
                    
                    if (methodValid) {
                        delegateMapping.put(methodElement, targetElement);
                    } else {
                        isComponentValid = false;
                    }
                }
            }
        }

        if (isComponentValid && !delegateMapping.isEmpty()) {
            generator.generateFacade(interfaceElement, delegateMapping, env);
        }
    }

    private boolean runRules(List<ValidationRule> rules, ValidationContext ctx) {
        boolean valid = true;
        for (ValidationRule rule : rules) {
            if (!rule.validate(ctx, reporter)) {
                valid = false;
            }
        }
        return valid;
    }
}
