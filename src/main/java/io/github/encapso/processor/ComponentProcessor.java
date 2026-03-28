package io.github.encapso.processor;

import io.github.encapso.Component;
import io.github.encapso.processor.domain.Reporter;
import io.github.encapso.processor.infrastructure.JavaPoetBuilderGenerator;
import io.github.encapso.processor.infrastructure.JavaPoetFacadeGenerator;
import io.github.encapso.processor.infrastructure.MessagerReporter;
import io.github.encapso.processor.domain.BoundaryRegistry;
import io.github.encapso.processor.usecase.ComponentBoundaryEnforcerUseCase;
import io.github.encapso.processor.usecase.ComponentProcessorUseCase;
import io.github.encapso.processor.usecase.DependencyAnalyzer;
import io.github.encapso.processor.validation.BoundaryTypeVisibilityRule;
import io.github.encapso.processor.validation.TargetClassVisibilityRule;
import io.github.encapso.processor.validation.TargetMethodSignatureRule;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes({
        "io.github.encapso.Component",
        "io.github.encapso.Api"
})
public class ComponentProcessor extends AbstractProcessor {

    private ComponentProcessorUseCase componentUseCase;
    private ComponentBoundaryEnforcerUseCase boundaryEnforcer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        javax.lang.model.util.Elements elements = processingEnv.getElementUtils();
        javax.lang.model.util.Types types = processingEnv.getTypeUtils();
        javax.annotation.processing.Filer filer = processingEnv.getFiler();
        javax.annotation.processing.Messager messager = processingEnv.getMessager();

        Reporter reporter = new MessagerReporter(messager);
        BoundaryRegistry registry = new BoundaryRegistry();

        DependencyAnalyzer dependencyAnalyzer = new DependencyAnalyzer(elements);

        this.componentUseCase = new ComponentProcessorUseCase(
                List.of(new TargetClassVisibilityRule()),
                List.of(new TargetMethodSignatureRule(), new BoundaryTypeVisibilityRule()),
                new JavaPoetFacadeGenerator(),
                new JavaPoetBuilderGenerator(),
                dependencyAnalyzer,
                registry,
                reporter,
                filer,
                elements,
                types,
                messager
        );

        this.boundaryEnforcer = new ComponentBoundaryEnforcerUseCase(registry, reporter, elements);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // Phase 1: Process all @Component interfaces (validate + generate + register boundaries)
        for (Element element : roundEnv.getElementsAnnotatedWith(Component.class)) {
            if (element instanceof TypeElement interfaceElement) {
                componentUseCase.processComponent(interfaceElement, roundEnv);
            }
        }

        // Phase 2: Enforce component boundaries across all compiled root elements
        boundaryEnforcer.enforce(roundEnv);

        return true;
    }
}
