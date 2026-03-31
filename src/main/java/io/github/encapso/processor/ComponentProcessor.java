package io.github.encapso.processor;

import io.github.encapso.Component;
import io.github.encapso.processor.domain.BoundaryRegistry;
import io.github.encapso.processor.domain.Reporter;
import io.github.encapso.processor.domain.ValidationContext;
import io.github.encapso.processor.infrastructure.JavaPoetBuilderGenerator;
import io.github.encapso.processor.infrastructure.JavaPoetFacadeGenerator;
import io.github.encapso.processor.infrastructure.MessagerReporter;
import io.github.encapso.processor.usecase.ComponentBoundaryEnforcerUseCase;
import io.github.encapso.processor.usecase.ComponentProcessorUseCase;
import io.github.encapso.processor.usecase.DependencyAnalyzer;
import io.github.encapso.processor.usecase.InstantiationPointSelector;
import io.github.encapso.processor.usecase.MetadataResolver;
import io.github.encapso.processor.usecase.PublicTypeScanner;
import io.github.encapso.processor.validation.BoundaryTypeVisibilityRule;
import io.github.encapso.processor.validation.ComponentInterfaceHierarchyRule;
import io.github.encapso.processor.validation.SingleComponentPerPackageRule;
import io.github.encapso.processor.validation.TargetClassVisibilityRule;
import io.github.encapso.processor.validation.TargetMethodSignatureRule;
import io.github.encapso.processor.validation.TargetClassInstantiatorRule;
import io.github.encapso.processor.validation.ApiAnnotationRule;
import io.github.encapso.processor.validation.ComponentInterfaceRule;
import io.github.encapso.Api;
import com.sun.source.util.Trees;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes({
        "io.github.encapso.Component",
        "io.github.encapso.Api",
        "*"
})
public class ComponentProcessor extends AbstractProcessor {

    private ComponentProcessorUseCase componentUseCase;
    private ComponentBoundaryEnforcerUseCase boundaryEnforcer;
    private ApiAnnotationRule apiRule;
    private Reporter reporter;
    private Elements elements;
    private Types types;
    private BoundaryRegistry registry;
    private Trees trees;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
        javax.annotation.processing.Filer filer = processingEnv.getFiler();
        javax.annotation.processing.Messager messager = processingEnv.getMessager();

        this.trees = Trees.instance(processingEnv);
        this.reporter = new MessagerReporter(messager, trees);
        this.registry = new BoundaryRegistry();
        this.apiRule = new ApiAnnotationRule();
        this.boundaryEnforcer = new ComponentBoundaryEnforcerUseCase(registry, reporter, elements, trees);

        InstantiationPointSelector selector = new InstantiationPointSelector(elements);
        DependencyAnalyzer dependencyAnalyzer = new DependencyAnalyzer(elements, selector);

        MetadataResolver metadataResolver = new MetadataResolver(
                List.of(new ComponentInterfaceRule(),
                         new TargetClassVisibilityRule(),
                         new TargetClassInstantiatorRule(),
                         new ComponentInterfaceHierarchyRule(),
                         new SingleComponentPerPackageRule(),
                         new TargetMethodSignatureRule(), 
                         new BoundaryTypeVisibilityRule()),
                elements,
                types
        );

        PublicTypeScanner publicTypeScanner = new PublicTypeScanner(elements);

        this.componentUseCase = new ComponentProcessorUseCase(
                metadataResolver,
                publicTypeScanner,
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

        this.boundaryEnforcer = new ComponentBoundaryEnforcerUseCase(registry, reporter, elements, trees);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        reporter.note("Encapso Round Processing: " + roundEnv.getRootElements().size() + " root elements");
        
        // Phase 1: Process all @Component interfaces (validate + generate + register boundaries)
        for (Element element : roundEnv.getElementsAnnotatedWith(Component.class)) {
            if (element instanceof TypeElement interfaceElement) {
                componentUseCase.processComponent(interfaceElement, roundEnv);
            }
        }

        // Phase 2: Process all @Api annotations (validate + register boundaries)
        for (Element element : roundEnv.getElementsAnnotatedWith(Api.class)) {
            if (element instanceof TypeElement typeElement) {
                ValidationContext ctx = new ValidationContext(null, null, typeElement, null, elements, types, roundEnv);
                if (apiRule.validate(ctx, reporter)) {
                    String pkg = elements.getPackageOf(typeElement).getQualifiedName().toString();
                    registry.registerPublicType(pkg, typeElement.getQualifiedName().toString());
                }
            }
        }

        if (!registry.isEmpty()) {
            reporter.note("Encapso boundaries active: " + registry.getBoundariesCount());
            // Phase 3: Enforce component boundaries across all compiled root elements
            boundaryEnforcer.enforce(roundEnv);
        }

        return false;
    }
}
