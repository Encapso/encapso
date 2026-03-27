package io.github.encapso.processor;

import io.github.encapso.Component;
import io.github.encapso.processor.domain.Reporter;
import io.github.encapso.processor.infrastructure.JavaPoetBuilderGenerator;
import io.github.encapso.processor.infrastructure.JavaPoetFacadeGenerator;
import io.github.encapso.processor.infrastructure.MessagerReporter;
import io.github.encapso.processor.usecase.ComponentProcessorUseCase;
import io.github.encapso.processor.usecase.DependencyAnalyzer;
import io.github.encapso.processor.validation.BoundaryTypeVisibilityRule;
import io.github.encapso.processor.validation.TargetClassVisibilityRule;
import io.github.encapso.processor.validation.TargetMethodSignatureRule;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes("io.github.encapso.Component")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class ComponentProcessor extends AbstractProcessor {

    private ComponentProcessorUseCase useCase;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (useCase == null) {
            Reporter reporter = new MessagerReporter(processingEnv.getMessager());
            useCase = new ComponentProcessorUseCase(
                    List.of(new TargetClassVisibilityRule()),
                    List.of(new TargetMethodSignatureRule(), new BoundaryTypeVisibilityRule()),
                    new JavaPoetFacadeGenerator(),
                    new JavaPoetBuilderGenerator(),
                    new DependencyAnalyzer(processingEnv),
                    reporter,
                    processingEnv
            );
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(Component.class)) {
            if (element instanceof TypeElement interfaceElement) {
                useCase.processComponent(interfaceElement);
            }
        }
        return true;
    }
}
