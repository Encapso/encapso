package io.github.encapso.processor.infrastructure;

import com.squareup.javapoet.*;
import io.github.encapso.processor.domain.BuilderGenerator;
import io.github.encapso.processor.domain.DependencyGraph;
import io.github.encapso.processor.domain.DependencyGraph.ExternalDependency;
import io.github.encapso.processor.domain.DependencyGraph.InstantiationStep;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Generates the ComponentBuilder using JavaPoet.
 */
public class JavaPoetBuilderGenerator extends BaseJavaPoetGenerator implements BuilderGenerator {

    @Override
    public void generateBuilder(TypeElement interfaceElement, GeneratorContext context) {
        String packageName = context.elements().getPackageOf(interfaceElement).getQualifiedName().toString();
        String builderName = interfaceElement.getSimpleName() + "Builder";
        ClassName facadeName = ClassName.get(interfaceElement);

        List<TypeVariableName> typeVariables = interfaceElement.getTypeParameters().stream()
                .map(TypeVariableName::get)
                .toList();

        TypeName builderTypeName = typeVariables.isEmpty()
                ? ClassName.get(packageName, builderName)
                : ParameterizedTypeName.get(ClassName.get(packageName, builderName), typeVariables.toArray(new TypeName[0]));

        TypeName facadeTypeName = typeVariables.isEmpty()
                ? facadeName
                : ParameterizedTypeName.get(facadeName, typeVariables.toArray(new TypeName[0]));

        TypeSpec.Builder builder = TypeSpec.classBuilder(builderName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addTypeVariables(typeVariables);

        // 1. Fields (Internal storage - use RAW types to avoid capture issues and simplify logic)
        addFields(builder, context.graph().externalDependencies());

        // 2. Private Constructor
        builder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .build());

        // 3. Static Factory Method
        MethodSpec.Builder newBuilder = MethodSpec.methodBuilder("newBuilder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addTypeVariables(typeVariables)
                .returns(builderTypeName);
        
        if (typeVariables.isEmpty()) {
            newBuilder.addStatement("return new $L()", builderName);
        } else {
            newBuilder.addStatement("return new $L<>()", builderName);
        }
        builder.addMethod(newBuilder.build());

        // 4. Setters (Public API - MUST use generics for type safety)
        addSetters(builder, context.graph().externalDependencies(), builderTypeName);

        // 5. Build Method
        addBuildMethod(builder, facadeTypeName, context.graph(), typeVariables);

        writeToFile(packageName, builder.build(), context);
    }

    private void addFields(TypeSpec.Builder builder, List<ExternalDependency> externalDependencies) {
        for (ExternalDependency dep : externalDependencies) {
            String internalName = getInternalFieldName(dep, externalDependencies);
            builder.addField(ClassName.get(dep.type()), internalName, Modifier.PRIVATE);
        }
    }

    private void addSetters(TypeSpec.Builder builder, List<ExternalDependency> externalDependencies, TypeName builderType) {
        for (ExternalDependency dep : externalDependencies) {
            String internalName = getInternalFieldName(dep, externalDependencies);
            
            // Use full TypeName (with generics) for setter parameter
            MethodSpec.Builder setter = MethodSpec.methodBuilder(dep.paramName())
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(TypeName.get(dep.type()), dep.paramName())
                    .returns(builderType);

            if (dep.required()) {
                setter.addStatement("this.$L = $T.requireNonNull($L, \"'$L' must not be null\")",
                        internalName, Objects.class, dep.paramName(), dep.paramName());
            } else {
                setter.addStatement("this.$L = $L", internalName, dep.paramName());
            }

            setter.addStatement("return this");
            builder.addMethod(setter.build());
        }
    }

    private void addBuildMethod(TypeSpec.Builder builder, TypeName facadeType, DependencyGraph graph, List<TypeVariableName> typeVariables) {
        MethodSpec.Builder buildMethod = MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                        .addMember("value", "$S", "unchecked")
                        .build())
                .returns(facadeType);

        // 1. Validate required dependencies
        for (ExternalDependency dep : graph.externalDependencies()) {
            if (dep.required()) {
                String internalName = getInternalFieldName(dep, graph.externalDependencies());
                buildMethod.addStatement("$T.requireNonNull($L, \"Required dependency '$L' was not provided to the builder\")",
                        Objects.class, internalName, dep.paramName());
            }
        }

        // 2. Instantiate internals in topological order (Using raw types for instantiation logic)
        for (InstantiationStep step : graph.instantiationSteps()) {
            // Map constructor arguments to local instance names or builder field names
            List<String> argExpressions = step.constructorArgs().stream()
                    .map(argName -> {
                        // Check if it's an external dependency in the builder
                        return graph.externalDependencies().stream()
                                .filter(d -> d.paramName().equals(argName))
                                .findFirst()
                                .map(d -> getInternalFieldName(d, graph.externalDependencies()))
                                .orElse(argName); // Otherwise it's a local instance name
                    })
                    .toList();
            
            String args = String.join(", ", argExpressions);
            ClassName targetClass = ClassName.get(step.type());
            
            if (step.factoryMethod() != null && !step.factoryMethod().isEmpty()) {
                buildMethod.addStatement("$T $L = $T.$L($L)", targetClass, step.instanceName(), 
                        targetClass, step.factoryMethod(), args);
            } else {
                buildMethod.addStatement("$T $L = new $T($L)", targetClass, step.instanceName(), 
                        targetClass, args);
            }
        }

        // 3. Return Facade implementation
        String facadeImplName;
        if (facadeType instanceof ParameterizedTypeName ptn) {
            facadeImplName = ((ClassName) ptn.rawType).simpleName() + "Impl";
        } else {
            facadeImplName = ((ClassName) facadeType).simpleName() + "Impl";
        }
        
        String facadeArgs = String.join(", ", graph.tcInstanceNames().values());
        
        if (typeVariables.isEmpty()) {
            buildMethod.addStatement("return new $L($L)", facadeImplName, facadeArgs);
        } else {
            buildMethod.addStatement("return new $L<>($L)", facadeImplName, facadeArgs);
        }

        builder.addMethod(buildMethod.build());
    }

    private String getInternalFieldName(ExternalDependency dep, List<ExternalDependency> allDeps) {
        long count = allDeps.stream()
                .filter(d -> d.paramName().equals(dep.paramName()))
                .count();
        
        if (count > 1) {
            // Collision! Use type suffix to disambiguate
            TypeName typeName = TypeName.get(dep.type());
            String typeSuffix;
            if (typeName instanceof ClassName cn) {
                typeSuffix = cn.simpleName();
            } else {
                typeSuffix = typeName.toString().replace("[]", "Array").replaceAll("[^a-zA-Z0-9]", "_");
            }
            return dep.paramName() + "_" + typeSuffix;
        }
        return dep.paramName();
    }
}
