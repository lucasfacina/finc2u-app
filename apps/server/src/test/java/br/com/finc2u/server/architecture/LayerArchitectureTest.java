package br.com.finc2u.server.architecture;

import br.com.finc2u.server.shared.model.BaseModel;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/*
 * As regras de camada do guia de classes: cada camada só conhece a de dentro. Analisa o `main` inteiro, e uma regra que não
 * encontra nenhuma classe para checar falha (o pacote mudou de nome e a regra ficou vazia).
 */
// Os campos @ArchTest são lidos pelo ArchUnit por reflexão: o aviso de "nunca usado" é falso.
@SuppressWarnings("unused")
@AnalyzeClasses(packages = "br.com.finc2u.server", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerArchitectureTest {

    @ArchTest
    static final ArchRule controllersDoNotDependOnRepositories = noClasses()
        .that().resideInAPackage("..controller..")
        .should().dependOnClassesThat().resideInAPackage("..repository..")
        .because("o controller só conversa com o service; ler direto do repository pularia as regras dele (o dono dos dados, por exemplo)");

    @ArchTest
    static final ArchRule controllersDoNotReturnEntities = methods()
        .that().arePublic().and().areDeclaredInClassesThat().resideInAPackage("..controller..")
        .should(notExposeAnEntity())
        .because("a entidade nunca sai pelo controller: a resposta é sempre um form (`ResponseDTO.from(entity)`)");

    @ArchTest
    static final ArchRule servicesDoNotKnowTheHttpLayer = noClasses()
        .that().resideInAPackage("..service..")
        .should().dependOnClassesThat().resideInAnyPackage("..dto..", "..form..", "..controller..",
            "org.springframework.http..", "org.springframework.web..", "jakarta.servlet..")
        .because("o service opera só com entidades e value objects; DTOs e objetos HTTP são do contorno");

    @ArchTest
    static final ArchRule entitiesDoNotKnowTheLayersAbove = noClasses()
        .that().resideInAPackage("..entity..")
        .should().dependOnClassesThat().resideInAnyPackage("..service..", "..repository..", "..controller..")
        .because("a entidade só tem regra do próprio estado (modelo rico); quem a usa é que conhece ela");

    @ArchTest
    static final ArchRule dtosAndFormsDoNotKnowServicesOrRepositories = noClasses()
        .that().resideInAnyPackage("..dto..", "..form..")
        .should().dependOnClassesThat().resideInAnyPackage("..service..", "..repository..")
        .because("request e response só transportam dados; a conversão cabe a eles, a orquestração não");

    @ArchTest
    static final ArchRule repositoriesDoNotOrderTextInTheDatabase = noMethods()
        .that().areDeclaredInClassesThat().resideInAPackage("..repository..")
        .should().haveNameMatching(".*OrderBy(Name|CardName|Description).*")
        .because("a ordem de um texto no banco depende da collation do servidor; quem entrega a lista ordena em Java com `TextOrder`");

    private static ArchCondition<JavaMethod> notExposeAnEntity() {
        return new ArchCondition<>("not return (or wrap) an entity") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                var leaked = method.getReturnType().getAllInvolvedRawTypes().stream()
                    .filter(type -> type.isAssignableTo(BaseModel.class))
                    .toList();
                if (leaked.isEmpty()) return;

                events.add(SimpleConditionEvent.violated(method,
                    method.getFullName() + " devolve a entidade " + leaked.getFirst().getSimpleName()));
            }
        };
    }

}
