package io.github.encapso.processor.usecase;

import io.github.encapso.engine.EncapsoEngine;
import io.github.encapso.processor.domain.CircularDependencyException;
import io.github.encapso.processor.domain.DependencyGraph;
import io.github.encapso.processor.domain.FacadeGenerator;
import io.github.encapso.processor.domain.BuilderGenerator;
import io.github.encapso.processor.domain.Reporter;
import io.github.encapso.processor.infrastructure.GeneratorContext;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates the processing of a single @Component interface.
 * Validates the interface structure, resolves dependencies, and triggers
 * generation.
 */
public class ComponentProcessorUseCase {

    private final MetadataResolver metadataResolver;
    private final PublicTypeScanner publicTypeScanner;
    private final FacadeGenerator facadeGenerator;
    private final BuilderGenerator builderGenerator;
    private final DependencyAnalyzer dependencyAnalyzer;
    private final EncapsoEngine engine;
    private final Reporter reporter;
    private final Filer filer;
    private final Elements elements;
    private final Types types;
    private final Messager messager;

    public ComponentProcessorUseCase(MetadataResolver metadataResolver,
                                     PublicTypeScanner publicTypeScanner,
                                     FacadeGenerator facadeGenerator,
                                     BuilderGenerator builderGenerator,
                                     DependencyAnalyzer dependencyAnalyzer,
                                     EncapsoEngine engine,
                                     Reporter reporter,
                                     Filer filer,
                                     Elements elements,
                                     Types types,
                                     Messager messager) {
        this.metadataResolver = metadataResolver;
        this.publicTypeScanner = publicTypeScanner;
        this.facadeGenerator = facadeGenerator;
        this.builderGenerator = builderGenerator;
        this.dependencyAnalyzer = dependencyAnalyzer;
        this.engine = engine;
        this.reporter = reporter;
        this.filer = filer;
        this.elements = elements;
        this.types = types;
        this.messager = messager;
    }

    public void processComponent(TypeElement interfaceElement, RoundEnvironment roundEnv) {
        // Phase 1: Metadata Resolution & Initial Validation
        MetadataResolver.ComponentMetadata metadata = metadataResolver.resolve(interfaceElement, roundEnv, reporter);
        if (!metadata.isValid() || metadata.delegateMapping().isEmpty())
            return;

        String componentPackage = elements.getPackageOf(interfaceElement).getQualifiedName().toString();

        try {
            // Phase 2: Dependency Analysis
            DependencyGraph graph = dependencyAnalyzer.analyze(interfaceElement, metadata.targetToFactory(), componentPackage);

            // Phase 3: Artifact Generation
            generateArtifacts(interfaceElement, metadata.delegateMapping(), graph);

            // Phase 4: Boundary Registration
            Set<String> publicTypes = publicTypeScanner.scan(interfaceElement, componentPackage, roundEnv);
            engine.registerComponent(componentPackage, interfaceElement.getQualifiedName().toString(), publicTypes);

        } catch (CircularDependencyException e) {
            reportCircularDependency(interfaceElement, e);
        }
    }

    private void generateArtifacts(TypeElement interfaceElement, Map<ExecutableElement, TypeElement> mapping, DependencyGraph graph) {
        GeneratorContext context = new GeneratorContext(filer, elements, types, messager, graph);
        facadeGenerator.generateFacade(interfaceElement, mapping, context);
        builderGenerator.generateBuilder(interfaceElement, context);
    }

    private void reportCircularDependency(TypeElement interfaceElement, CircularDependencyException e) {
        List<TypeElement> cycle = e.getCycle();
        if (cycle.size() <= 2 && cycle.get(0).equals(cycle.get(cycle.size() - 1))) {
            reporter.error("Self-Circular dependency detected: Target class '" + cycle.get(0).getSimpleName() + "' depends on itself via its constructor.", interfaceElement);
            return;
        }

        String cyclePath = cycle.stream()
                .map(te -> te.getSimpleName().toString())
                .reduce((a, b) -> a + " -> " + b)
                .orElse("");
        reporter.error("Circular dependency detected in internal components: " + cyclePath, interfaceElement);
    }
}
