package io.github.encapso.integration;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.encapso.processor.ComponentProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

class GenericsCompilationIntegrationTest {

    @Test
    @DisplayName("Should support generic @Component interfaces (Generic Facades)")
    void shouldSupportGenericFacades() {
        JavaFileObject service = JavaFileObjects.forSourceLines(
                "repo.UserService",
                "package repo;",
                "public class UserService {",
                "    public String save(String user) { return user; }",
                "}"
        );

        JavaFileObject facade = JavaFileObjects.forSourceLines(
                "repo.UserFacade",
                "package repo;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "@Component",
                "public interface UserFacade<T> {",
                "    @DelegateTo(UserService.class)",
                "    T save(T entity);",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(service, facade);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("repo.UserFacadeImpl");
        assertThat(compilation).generatedSourceFile("repo.UserFacadeBuilder");
    }

    @Test
    @DisplayName("Should support target classes with generic type parameters")
    void shouldSupportGenericTargetClasses() {
        JavaFileObject genericService = JavaFileObjects.forSourceLines(
                "repo.GenericService",
                "package repo;",
                "public class GenericService<T> {",
                "    public T process(T input) { return input; }",
                "}"
        );

        JavaFileObject facade = JavaFileObjects.forSourceLines(
                "repo.StringFacade",
                "package repo;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "@Component",
                "public interface StringFacade {",
                "    @DelegateTo(GenericService.class)",
                "    String process(String input);",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(genericService, facade);

        assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Should recursively whitelist types in complex nested generics and arrays")
    void shouldRecursivelyWhitelistNestedGenerics() {
        JavaFileObject internalDto = JavaFileObjects.forSourceLines(
                "billing.InternalData",
                "package billing;",
                "public class InternalData {}"
        );

        JavaFileObject service = JavaFileObjects.forSourceLines(
                "billing.BillingService",
                "package billing;",
                "import java.util.*;",
                "public class BillingService {",
                "    public List<Map<String, InternalData[]>> batchProcess(List<? extends InternalData> data) { return null; }",
                "}"
        );

        JavaFileObject facade = JavaFileObjects.forSourceLines(
                "billing.BillingFacade",
                "package billing;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "import java.util.*;",
                "@Component",
                "public interface BillingFacade {",
                "    @DelegateTo(BillingService.class)",
                "    List<Map<String, InternalData[]>> batchProcess(List<? extends InternalData> data);",
                "}"
        );

        // UI code (external) using the "InternalData" which should be whitelisted because it's in the signature
        JavaFileObject external = JavaFileObjects.forSourceLines(
                "ui.Dashboard",
                "package ui;",
                "import billing.InternalData;",
                "public class Dashboard {",
                "    private InternalData data;",
                "    private InternalData[] dataArray;",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(internalDto, service, facade, external);

        assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Should support delegation to raw generic target classes with implicit casting")
    void shouldSupportRawDelegationToGenericService() {
        JavaFileObject genericService = JavaFileObjects.forSourceLines(
                "repo.GenericService",
                "package repo;",
                "public class GenericService<T> {",
                "    public T find(String id) { return null; }",
                "}"
        );

        JavaFileObject facade = JavaFileObjects.forSourceLines(
                "repo.UserFacade",
                "package repo;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "@Component",
                "public interface UserFacade {",
                "    @DelegateTo(GenericService.class)",
                "    String find(String id);",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(genericService, facade);

        assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Should propagate generic type parameters to builder setters")
    void shouldPropagateGenericTypesToBuilderSetters() {
        JavaFileObject repository = JavaFileObjects.forSourceLines(
                "repo.GenericRepository",
                "package repo;",
                "public class GenericRepository<T> {",
                "    public T find(String id) { return null; }",
                "}"
        );

        JavaFileObject service = JavaFileObjects.forSourceLines(
                "repo.InternalService",
                "package repo;",
                "public class InternalService<T> {",
                "    private final GenericRepository<T> repository;",
                "    public InternalService(GenericRepository<T> repository) {",
                "        this.repository = repository;",
                "    }",
                "    public T find(String id) { return repository.find(id); }",
                "}"
        );

        JavaFileObject facade = JavaFileObjects.forSourceLines(
                "repo.UserFacade",
                "package repo;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "@Component",
                "public interface UserFacade<T> {",
                "    @DelegateTo(InternalService.class)",
                "    T find(String id);",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(repository, service, facade);

        assertThat(compilation).succeeded();
        // Here we'd ideally want to check the generated source to ensure 'repository(GenericRepository<T>)'
        // instead of 'repository(GenericRepository)'
    }

    @Test
    @DisplayName("Should support bounded generics and propagate them correctly to the builder")
    void shouldSupportBoundedGenericsInBuilder() {
        JavaFileObject baseInterface = JavaFileObjects.forSourceLines(
                "repo.Base",
                "package repo;",
                "public interface Base { String getId(); }"
        );

        JavaFileObject implementation = JavaFileObjects.forSourceLines(
                "repo.Implementation",
                "package repo;",
                "import io.github.encapso.Api;",
                "@Api",
                "public class Implementation implements Base {",
                "    public String getId() { return \"impl\"; }",
                "}"
        );

        JavaFileObject repository = JavaFileObjects.forSourceLines(
                "repo.GenericRepository",
                "package repo;",
                "import io.github.encapso.Api;",
                "@Api",
                "public class GenericRepository<T extends Base> {",
                "    public T find(String id) { return null; }",
                "}"
        );

        JavaFileObject service = JavaFileObjects.forSourceLines(
                "repo.InternalService",
                "package repo;",
                "public class InternalService<T extends Base> {",
                "    private final GenericRepository<T> repository;",
                "    public InternalService(GenericRepository<T> repository) {",
                "        this.repository = repository;",
                "    }",
                "    public T find(String id) { return repository.find(id); }",
                "}"
        );

        JavaFileObject facade = JavaFileObjects.forSourceLines(
                "repo.UserFacade",
                "package repo;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "@Component",
                "public interface UserFacade<T extends Base> {",
                "    @DelegateTo(InternalService.class)",
                "    T find(String id);",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(baseInterface, implementation, repository, service, facade);

        assertThat(compilation).succeeded();
        
        // Final sanity check: Can we actually USE the generated builder in a type-safe way?
        // We'll compile a small piece of client code that uses the generated classes.
        JavaFileObject client = JavaFileObjects.forSourceLines(
                "client.Client",
                "package client;",
                "import repo.UserFacade;",
                "import repo.UserFacadeBuilder;",
                "import repo.GenericRepository;",
                "import repo.Implementation;",
                "public class Client {",
                "    public void test() {",
                "        UserFacade<Implementation> facade = UserFacadeBuilder.<Implementation>newBuilder()",
                "            .repository(new GenericRepository<Implementation>())",
                "            .build();",
                "    }",
                "}"
        );

        Compilation clientCompilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(baseInterface, implementation, repository, service, facade, client);

        assertThat(clientCompilation).succeeded();
    }

    @Test
    @DisplayName("Should support lower-bounded generic wildcards (? super T)")
    void shouldSupportLowerBoundedWildcards() {
        JavaFileObject base = JavaFileObjects.forSourceLines(
                "repo.Base",
                "package repo;",
                "public interface Base {}"
        );
        JavaFileObject service = JavaFileObjects.forSourceLines(
                "repo.ConsumerService",
                "package repo;",
                "import java.util.List;",
                "public class ConsumerService<T> {",
                "    public void consume(List<? super T> list, T item) {",
                "        // Target implementation",
                "    }",
                "}"
        );
        JavaFileObject facade = JavaFileObjects.forSourceLines(
                "repo.ConsumerFacade",
                "package repo;",
                "import io.github.encapso.Component;",
                "import io.github.encapso.DelegateTo;",
                "import java.util.List;",
                "@Component",
                "public interface ConsumerFacade<T> {",
                "    @DelegateTo(ConsumerService.class)",
                "    void consume(List<? super T> list, T item);",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ComponentProcessor())
                .compile(base, service, facade);

        assertThat(compilation).succeeded();
    }
}
