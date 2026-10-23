package br.com.finc2u.server.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/*
 * A documentação da API não pode envelhecer: endpoint novo sem descrição quebra o build, como acontece com as regras de camada.
 */
// Os campos @ArchTest são lidos pelo ArchUnit por reflexão: o aviso de "nunca usado" é falso.
@SuppressWarnings("unused")
@AnalyzeClasses(packages = "br.com.finc2u.server", importOptions = ImportOption.DoNotIncludeTests.class)
class ApiDocumentationArchitectureTest {

    @ArchTest
    static final ArchRule controllersAreTagged = classes()
        .that().areAnnotatedWith(RestController.class)
        .should().beAnnotatedWith(Tag.class)
        .because("o Swagger agrupa os endpoints pela `@Tag`; sem ela o controller aparece com o nome da classe, sem descrição");

    @ArchTest
    static final ArchRule endpointsAreDescribed = methods()
        .that().areMetaAnnotatedWith(RequestMapping.class)
        .or().areAnnotatedWith(RequestMapping.class)
        .should().beAnnotatedWith(Operation.class)
        .because("cada endpoint precisa de resumo, descrição e `operationId` (o cliente do frontend é gerado a partir deles)");

    @ArchTest
    static final ArchRule domainDoesNotKnowSwagger = noClasses()
        .that().resideInAnyPackage("..entity..", "..enums..", "..vo..", "..service..", "..repository..")
        .should().dependOnClassesThat().resideInAnyPackage("io.swagger..", "org.springdoc..")
        .because("a documentação da API é do contorno (controller, dto, form e config); o domínio não depende de biblioteca de apresentação");

}
