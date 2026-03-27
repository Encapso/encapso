package io.github.encapso.processor.usecase;

import io.github.encapso.processor.ProcessorUtils;
import io.github.encapso.processor.domain.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import java.util.*;

public class ComponentProcessorUseCase {

    private final List<ValidationRule> criticalRules;
    private final List<ValidationRule> signatureRules;
    private final FacadeGenerator facadeGenerator;
    private final BuilderGenerator builderGenerator;
    private final DependencyAnalyzer dependencyAnalyzer;
    private final Reporter reporter;
    private final ProcessingEnvironment env;

    public ComponentProcessorUseCase(List<ValidationRule> criticalRules,
                                     List<ValidationRule> signatureRules,
                                     FacadeGenerator facadeGenerator,
                                     BuilderGenerator builderGenerator,
                                     DependencyAnalyzer dependencyAnalyzer,
                                     Reporter reporter,
                                     ProcessingEnvironment env) {
        this.criticalRules = criticalRules;
        this.signatureRules = signatureRules;
        this.facadeGenerator = facadeGenerator;
        this.builderGenerator = builderGenerator;
        this.dependencyAnalyzer = dependencyAnalyzer;
        this.reporter = reporter;
        this.env = env;
    }

    public void processComponent(TypeElement interfaceElement) {
        boolean isValid = true;
        Map<ExecutableElement, TypeElement> delegateMapping = new LinkedHashMap<>();
        Set<TypeElement> uniqueTargetClasses = new LinkedHashSet<>();

        for (Element enclosed : interfaceElement.getEnclosedElements()) {
            if (!(enclosed instanceof ExecutableElement methodElement)) continue;

            Optional<TypeElement> optTarget = ProcessorUtils.getDelegateTargetElement(methodElement, env);
            if (optTarget.isEmpty()) continue;

            TypeElement target = optTarget.get();
            ValidationContext ctx = new ValidationContext(interfaceElement, methodElement, target, env);

            if (!runRules(criticalRules, ctx)) {
                isValid = false;
                continue;
            }

            if (runRules(signatureRules, ctx)) {
                delegateMapping.put(methodElement, target);
                uniqueTargetClasses.add(target);
            } else {
                isValid = false;
            }
        }

        if (isValid && !delegateMapping.isEmpty()) {
            String componentPackage = env.getElementUtils()
                    .getPackageOf(interfaceElement).getQualifiedName().toString();
            DependencyGraph graph = dependencyAnalyzer.analyze(uniqueTargetClasses, componentPackage);
            facadeGenerator.generateFacade(interfaceElement, delegateMapping, graph, env);
            builderGenerator.generateBuilder(interfaceElement, graph, env);
        }
    }

    private boolean runRules(List<ValidationRule> rules, ValidationContext ctx) {
        boolean valid = true;
        for (ValidationRule rule : rules) {
            if (!rule.validate(ctx, reporter)) valid = false;
        }
        return valid;
    }
}
