package io.github.encapso.processor.usecase;

import io.github.encapso.processor.ProcessorUtils;
import io.github.encapso.processor.domain.BoundaryRegistry;
import io.github.encapso.processor.domain.BuilderGenerator;
import io.github.encapso.processor.domain.CircularDependencyException;
import io.github.encapso.processor.domain.DependencyGraph;
import io.github.encapso.processor.domain.FacadeGenerator;
import io.github.encapso.processor.domain.Reporter;
import io.github.encapso.processor.domain.ValidationContext;
import io.github.encapso.processor.domain.ValidationRule;
import io.github.encapso.processor.validation.ComponentInterfaceHierarchyRule;
import io.github.encapso.processor.infrastructure.GeneratorContext;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Orchestrates the processing of a single @Component interface.
 * Validates the interface structure, resolves dependencies, and triggers
 * generation.
 */
public class ComponentProcessorUseCase {

    private final List<ValidationRule> criticalRules;
    private final List<ValidationRule> signatureRules;
    private final FacadeGenerator facadeGenerator;
    private final BuilderGenerator builderGenerator;
    private final DependencyAnalyzer dependencyAnalyzer;
    private final BoundaryRegistry boundaryRegistry;
    private final Reporter reporter;
    private final Filer filer;
    private final Elements elements;
    private final Types types;
    private final Messager messager;

    public ComponentProcessorUseCase(List<ValidationRule> criticalRules,
            List<ValidationRule> signatureRules,
            FacadeGenerator facadeGenerator,
            BuilderGenerator builderGenerator,
            DependencyAnalyzer dependencyAnalyzer,
            BoundaryRegistry boundaryRegistry,
            Reporter reporter,
            Filer filer,
            Elements elements,
            Types types,
            Messager messager) {
        this.criticalRules = criticalRules;
        this.signatureRules = signatureRules;
        this.facadeGenerator = facadeGenerator;
        this.builderGenerator = builderGenerator;
        this.dependencyAnalyzer = dependencyAnalyzer;
        this.boundaryRegistry = boundaryRegistry;
        this.reporter = reporter;
        this.filer = filer;
        this.elements = elements;
        this.types = types;
        this.messager = messager;
    }

    public void processComponent(TypeElement interfaceElement, RoundEnvironment roundEnv) {
        ComponentMetadata metadata = collectComponentMetadata(interfaceElement);
        if (!metadata.isValid() || metadata.delegateMapping().isEmpty())
            return;

        String componentPackage = elements.getPackageOf(interfaceElement).getQualifiedName().toString();

        try {
            DependencyGraph graph = dependencyAnalyzer.analyze(interfaceElement, metadata.targetToFactory(),
                    componentPackage);
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
        Map<TypeElement, String> targetToFactory = new LinkedHashMap<>();
        Set<TypeElement> uniqueTargetClasses = new LinkedHashSet<>();
        boolean isValid = true;

        // 1. Initial Interface Validation (Component-level rules)
        ValidationContext initialCtx = new ValidationContext(interfaceElement, null, null, elements, types);
        if (!runRules(criticalRules.stream()
                .filter(r -> r instanceof ComponentInterfaceHierarchyRule)
                .toList(), initialCtx)) {
            isValid = false;
        }

        // 2. Method-level Validation
        for (Element enclosed : interfaceElement.getEnclosedElements()) {
            if (enclosed instanceof ExecutableElement method) {
                Optional<io.github.encapso.processor.ProcessorUtils.DelegateRequest> request = 
                        io.github.encapso.processor.ProcessorUtils.getDelegateRequest(method, types);
                
                if (request.isEmpty())
                    continue;

                TypeElement target = request.get().target();
                ValidationContext ctx = new ValidationContext(interfaceElement, method, target, elements, types);

                // Run critical rules that are NOT component-level (method-level critical rules)
                if (!runRules(criticalRules.stream()
                        .filter(r -> !(r instanceof ComponentInterfaceHierarchyRule))
                        .toList(), ctx)) {
                    isValid = false;
                    continue;
                }

                if (runRules(signatureRules, ctx)) {
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

    private void generateArtifacts(TypeElement interfaceElement, Map<ExecutableElement, TypeElement> mapping,
            DependencyGraph graph) {
        GeneratorContext context = new GeneratorContext(filer, elements, types, messager, graph);
        facadeGenerator.generateFacade(interfaceElement, mapping, context);
        builderGenerator.generateBuilder(interfaceElement, context);
    }

    private void registerComponentBoundary(TypeElement interfaceElement, String componentPackage,
            RoundEnvironment roundEnv) {
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
        TypeElement apiAnnotation = elements.getTypeElement("io.github.encapso.Api");
        if (apiAnnotation != null) {
            for (Element apiElement : roundEnv.getElementsAnnotatedWith(apiAnnotation)) {
                if (apiElement instanceof TypeElement te) {
                    String tePackage = elements.getPackageOf(te).getQualifiedName().toString();
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
            if (!rule.validate(ctx, reporter))
                valid = false;
        }
        return valid;
    }

    private void collectSignatureTypes(TypeMirror mirror, Set<String> allowed) {
        if (mirror instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) {
            allowed.add(te.getQualifiedName().toString());
            dt.getTypeArguments().forEach(arg -> collectSignatureTypes(arg, allowed));
        } else if (mirror instanceof WildcardType wt) {
            if (wt.getExtendsBound() != null)
                collectSignatureTypes(wt.getExtendsBound(), allowed);
            if (wt.getSuperBound() != null)
                collectSignatureTypes(wt.getSuperBound(), allowed);
        } else if (mirror instanceof ArrayType at) {
            collectSignatureTypes(at.getComponentType(), allowed);
        }
    }

    private record ComponentMetadata(
            boolean isValid,
            Map<ExecutableElement, TypeElement> delegateMapping,
            Map<TypeElement, String> targetToFactory,
            Set<TypeElement> targetClasses) {
    }
}
