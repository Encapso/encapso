package io.github.encapso.processor.usecase;

import io.github.encapso.processor.ProcessorUtils;
import io.github.encapso.processor.domain.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.*;

/**
 * Orchestrates the processing of a single @Component interface.
 * Validates the interface structure, resolves dependencies, and triggers generation.
 */
public class ComponentProcessorUseCase {

    private final List<ValidationRule> criticalRules;
    private final List<ValidationRule> signatureRules;
    private final FacadeGenerator facadeGenerator;
    private final BuilderGenerator builderGenerator;
    private final DependencyAnalyzer dependencyAnalyzer;
    private final BoundaryRegistry boundaryRegistry;
    private final Reporter reporter;
    private final ProcessingEnvironment env;

    public ComponentProcessorUseCase(List<ValidationRule> criticalRules,
                                     List<ValidationRule> signatureRules,
                                     FacadeGenerator facadeGenerator,
                                     BuilderGenerator builderGenerator,
                                     DependencyAnalyzer dependencyAnalyzer,
                                     BoundaryRegistry boundaryRegistry,
                                     Reporter reporter,
                                     ProcessingEnvironment env) {
        this.criticalRules = criticalRules;
        this.signatureRules = signatureRules;
        this.facadeGenerator = facadeGenerator;
        this.builderGenerator = builderGenerator;
        this.dependencyAnalyzer = dependencyAnalyzer;
        this.boundaryRegistry = boundaryRegistry;
        this.reporter = reporter;
        this.env = env;
    }

    public void processComponent(TypeElement interfaceElement, javax.annotation.processing.RoundEnvironment roundEnv) {
        ComponentMetadata metadata = collectComponentMetadata(interfaceElement);
        if (!metadata.isValid() || metadata.delegateMapping().isEmpty()) return;

        String componentPackage = env.getElementUtils()
                .getPackageOf(interfaceElement).getQualifiedName().toString();

        try {
            DependencyGraph graph = dependencyAnalyzer.analyze(metadata.targetClasses(), componentPackage);
            generateArtifacts(interfaceElement, metadata.delegateMapping(), graph);
            registerComponentBoundary(interfaceElement, componentPackage, roundEnv);
        } catch (CircularDependencyException e) {
            String cyclePath = e.getCycle().stream()
                    .map(te -> te.getSimpleName().toString())
                    .reduce((a, b) -> a + " -> " + b)
                    .orElse("");
            reporter.error("Circular dependency detected in internal components: " + cyclePath, interfaceElement);
        }
    }

    // --- Core Phases ---

    private ComponentMetadata collectComponentMetadata(TypeElement interfaceElement) {
        Map<ExecutableElement, TypeElement> delegateMapping = new LinkedHashMap<>();
        Set<TypeElement> uniqueTargetClasses = new LinkedHashSet<>();
        boolean isValid = true;

        for (Element enclosed : interfaceElement.getEnclosedElements()) {
            if (enclosed instanceof ExecutableElement method) {
                Optional<TypeElement> target = ProcessorUtils.getDelegateTargetElement(method, env);
                if (target.isEmpty()) continue;

                ValidationContext ctx = new ValidationContext(interfaceElement, method, target.get(), env);

                if (!runRules(criticalRules, ctx)) {
                    isValid = false;
                    continue;
                }

                if (runRules(signatureRules, ctx)) {
                    delegateMapping.put(method, target.get());
                    uniqueTargetClasses.add(target.get());
                } else {
                    isValid = false;
                }
            }
        }
        return new ComponentMetadata(isValid, delegateMapping, uniqueTargetClasses);
    }

    private void generateArtifacts(TypeElement interfaceElement, Map<ExecutableElement, TypeElement> mapping, DependencyGraph graph) {
        facadeGenerator.generateFacade(interfaceElement, mapping, graph, env);
        builderGenerator.generateBuilder(interfaceElement, graph, env);
    }

    private void registerComponentBoundary(TypeElement interfaceElement, String componentPackage, javax.annotation.processing.RoundEnvironment roundEnv) {
        Set<String> allowedTypes = new LinkedHashSet<>();

        // The interface and its generated builder are always allowed
        allowedTypes.add(interfaceElement.getQualifiedName().toString());
        allowedTypes.add(componentPackage + "." + interfaceElement.getSimpleName() + "Builder");

        // Collect all types explicitly referenced in method signatures
        for (Element member : interfaceElement.getEnclosedElements()) {
            if (member instanceof ExecutableElement method) {
                collectSignatureTypes(method.getReturnType(), allowedTypes);
                method.getParameters().forEach(p -> collectSignatureTypes(p.asType(), allowedTypes));
                method.getThrownTypes().forEach(t -> collectSignatureTypes(t, allowedTypes));
            }
        }

        // --- NEW: Collect types explicitly annotated with @Api in the same package tree ---
        // Find the Api annotation type element
        TypeElement apiAnnotation = env.getElementUtils().getTypeElement("io.github.encapso.Api");
        if (apiAnnotation != null) {
            for (Element apiElement : roundEnv.getElementsAnnotatedWith(apiAnnotation)) {
                if (apiElement instanceof TypeElement te) {
                    String tePackage = env.getElementUtils().getPackageOf(te).getQualifiedName().toString();
                    if (tePackage.startsWith(componentPackage)) {
                        allowedTypes.add(te.getQualifiedName().toString());
                    }
                }
            }
        }

        boundaryRegistry.register(componentPackage, allowedTypes);
    }

    // --- Helpers ---

    private boolean runRules(List<ValidationRule> rules, ValidationContext ctx) {
        boolean valid = true;
        for (ValidationRule rule : rules) {
            if (!rule.validate(ctx, reporter)) valid = false;
        }
        return valid;
    }

    private void collectSignatureTypes(TypeMirror mirror, Set<String> allowed) {
        if (mirror instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) {
            allowed.add(te.getQualifiedName().toString());
            dt.getTypeArguments().forEach(arg -> collectSignatureTypes(arg, allowed));
        }
    }

    private record ComponentMetadata(
            boolean isValid,
            Map<ExecutableElement, TypeElement> delegateMapping,
            Set<TypeElement> targetClasses
    ) {}
}
