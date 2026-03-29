package io.github.encapso.processor.infrastructure;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;
import io.github.encapso.processor.domain.FacadeGenerator;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates the Facade implementation using JavaPoet.
 */
public class JavaPoetFacadeGenerator extends BaseJavaPoetGenerator implements FacadeGenerator {

    @Override
    public void generateFacade(TypeElement interfaceElement, Map<ExecutableElement, TypeElement> mapping,
                               GeneratorContext context) {
        String packageName = context.elements().getPackageOf(interfaceElement).getQualifiedName().toString();
        String implName = interfaceElement.getSimpleName().toString() + "Impl";

        List<TypeVariableName> typeVariables = interfaceElement.getTypeParameters().stream()
                .map(TypeVariableName::get)
                .toList();

        TypeSpec.Builder facadeImpl = TypeSpec.classBuilder(implName)
                .addModifiers(Modifier.FINAL)
                .addTypeVariables(typeVariables)
                .addSuperinterface(TypeName.get(interfaceElement.asType()));

        if (!typeVariables.isEmpty()) {
            facadeImpl.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                    .addMember("value", "$S", "unchecked")
                    .build());
        }

        addFields(facadeImpl, context.graph().tcInstanceNames());
        addConstructor(facadeImpl, context.graph().tcInstanceNames());
        addMethods(facadeImpl, interfaceElement, mapping, context.graph().tcInstanceNames(), context.types());

        writeToFile(packageName, facadeImpl.build(), context);
    }

    private void addFields(TypeSpec.Builder builder, Map<TypeElement, String> instanceNames) {
        for (Map.Entry<TypeElement, String> entry : instanceNames.entrySet()) {
            builder.addField(FieldSpec.builder(ClassName.get(entry.getKey()), entry.getValue(), Modifier.PRIVATE, Modifier.FINAL)
                    .build());
        }
    }

    private void addConstructor(TypeSpec.Builder builder, Map<TypeElement, String> instanceNames) {
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder();
        for (Map.Entry<TypeElement, String> entry : instanceNames.entrySet()) {
            String name = entry.getValue();
            constructor.addParameter(ClassName.get(entry.getKey()), name)
                    .addStatement("this.$L = $L", name, name);
        }
        builder.addMethod(constructor.build());
    }

    private void addMethods(TypeSpec.Builder builder, TypeElement interfaceElement,
                            Map<ExecutableElement, TypeElement> mapping,
                            Map<TypeElement, String> instanceNames,
                            Types types) {
        DeclaredType owner = (DeclaredType) interfaceElement.asType();
        List<TypeVariableName> facadeTypeVars = interfaceElement.getTypeParameters().stream()
                .map(TypeVariableName::get)
                .toList();
        Set<String> allowedVars = facadeTypeVars.stream().map(v -> v.name).collect(Collectors.toSet());

        for (Map.Entry<ExecutableElement, TypeElement> entry : mapping.entrySet()) {
            ExecutableElement facadeMethod = entry.getKey();
            TypeElement targetClass = entry.getValue();
            String instanceName = instanceNames.get(targetClass);

            MethodSpec.Builder bridge = MethodSpec.overriding(facadeMethod, owner, types);
            MethodSpec bridgeSpec = bridge.build();
            
            ExecutableElement targetMethod = findTargetMethod(targetClass, facadeMethod);

            StringBuilder call = new StringBuilder();
            if (facadeMethod.getReturnType().getKind() != TypeKind.VOID) {
                if (needsReturnCast(facadeMethod, targetMethod)) {
                    call.append("return ($T) ");
                } else {
                    call.append("return ");
                }
            }
            
            call.append("this.").append(instanceName).append(".").append(facadeMethod.getSimpleName()).append("(");
            
            List<String> argExpressions = new java.util.ArrayList<>();
            List<Object> callParams = new java.util.ArrayList<>();
            if (needsReturnCast(facadeMethod, targetMethod) && facadeMethod.getReturnType().getKind() != TypeKind.VOID) {
                callParams.add(bridgeSpec.returnType);
            }

            for (int i = 0; i < facadeMethod.getParameters().size(); i++) {
                VariableElement p = facadeMethod.getParameters().get(i);
                String name = p.getSimpleName().toString();
                if (targetMethod != null && i < targetMethod.getParameters().size()) {
                    TypeName targetParamType = TypeName.get(targetMethod.getParameters().get(i).asType());
                    // Sanitize the target param type to current facade scope
                    TypeName sanitizedTargetType = sanitize(targetParamType, allowedVars);
                    TypeName facadeParamType = TypeName.get(p.asType());
                    
                    if (!facadeParamType.equals(sanitizedTargetType) || facadeParamType instanceof TypeVariableName) {
                        argExpressions.add("($T) " + name);
                        callParams.add(sanitizedTargetType);
                    } else {
                        argExpressions.add(name);
                    }
                } else {
                    argExpressions.add(name);
                }
            }
            
            call.append(String.join(", ", argExpressions)).append(")");
            
            bridge.addStatement(call.toString(), callParams.toArray());
            builder.addMethod(bridge.build());
        }
    }

    private ExecutableElement findTargetMethod(TypeElement targetClass, ExecutableElement facadeMethod) {
        return ElementFilter.methodsIn(targetClass.getEnclosedElements()).stream()
                .filter(m -> m.getSimpleName().equals(facadeMethod.getSimpleName()))
                .filter(m -> m.getParameters().size() == facadeMethod.getParameters().size())
                .findFirst()
                .orElse(null);
    }

    private boolean needsReturnCast(ExecutableElement facadeMethod, ExecutableElement targetMethod) {
        if (facadeMethod.getReturnType().getKind() == TypeKind.TYPEVAR) return true;
        if (targetMethod == null) return false;
        TypeName facadeReturn = TypeName.get(facadeMethod.getReturnType());
        TypeName targetReturn = TypeName.get(targetMethod.getReturnType());
        return !facadeReturn.equals(targetReturn);
    }

    private TypeName sanitize(TypeName type, Set<String> allowedVars) {
        if (type instanceof TypeVariableName tv) {
            return allowedVars.contains(tv.name) ? tv : TypeName.OBJECT;
        }
        if (type instanceof ParameterizedTypeName ptn) {
            TypeName[] args = ptn.typeArguments.stream()
                    .map(arg -> sanitize(arg, allowedVars))
                    .toArray(TypeName[]::new);
            return ParameterizedTypeName.get(ptn.rawType, args);
        }
        return type;
    }
}
