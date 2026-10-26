package br.com.finc2u.server.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Optional;
import java.util.regex.Pattern;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/*
 * As regras entre features e de determinismo: uma feature só enxerga o service da outra, `shared` não depende de nenhuma feature
 * e nada que deva ser puro lê o relógio ("hoje" vem do `Clock` e chega por parâmetro).
 */
// Os campos @ArchTest são lidos pelo ArchUnit por reflexão: o aviso de "nunca usado" é falso.
@SuppressWarnings("unused")
@AnalyzeClasses(packages = "br.com.finc2u.server", importOptions = ImportOption.DoNotIncludeTests.class)
class FeatureArchitectureTest {

    private static final Pattern FEATURE = Pattern.compile("^br\\.com\\.finc2u\\.server\\.features\\.([a-z]+)\\.");

    @ArchTest
    static final ArchRule featuresHaveNoCycles = slices()
        .matching("br.com.finc2u.server.features.(*)..")
        .should().beFreeOfCycles()
        .because("a dependência entre features aponta num sentido só (as voltas passam por evento ou por porta)");

    @ArchTest
    static final ArchRule featuresDoNotUseTheRepositoryOfAnother = classes()
        .that().resideInAPackage("br.com.finc2u.server.features..")
        .should(notUseTheRepositoryOfAnotherFeature())
        .because("o repository é detalhe de persistência da feature dona; as outras chamam o service dela");

    @ArchTest
    static final ArchRule sharedDoesNotDependOnFeatures = noClasses()
        .that().resideInAPackage("br.com.finc2u.server.shared..")
        .should().dependOnClassesThat().resideInAPackage("br.com.finc2u.server.features..")
        .because("o shared é transversal: se conhecesse uma feature, deixaria de ser compartilhado");

    @ArchTest
    static final ArchRule pureClassesDoNotReadTheClock = noClasses()
        .that().resideInAnyPackage("..entity..", "..specification..", "..form..", "..vo..")
        .should().callMethodWhere(target(nameMatching("now|currentTimeMillis|nanoTime")))
        .orShould().dependOnClassesThat().areAssignableTo(java.time.Clock.class)
        .because("a mesma entrada precisa dar sempre a mesma saída: \"hoje\" vem do `Clock` dos services e chega por parâmetro");

    @ArchTest
    static final ArchRule requestsDoNotUseDateConstraintsThatReadTheSystemClock = noClasses()
        .that().resideInAPackage("..dto..")
        .should().dependOnClassesThat().haveNameMatching("jakarta\\.validation\\.constraints\\.(Future|FutureOrPresent|Past|PastOrPresent)")
        .because("essas constraints leem o relógio do sistema e escapam do `Clock`: a regra de data vai para a entidade ou o service, que recebem \"hoje\" por parâmetro");

    private static ArchCondition<JavaClass> notUseTheRepositoryOfAnotherFeature() {
        return new ArchCondition<>("not depend on a repository of another feature") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                var own = featureOf(javaClass);
                if (own.isEmpty()) return;

                javaClass.getDirectDependenciesFromSelf().stream()
                    .filter(dependency -> dependency.getTargetClass().getPackageName().contains(".repository"))
                    .filter(dependency -> featureOf(dependency.getTargetClass()).filter(other -> !other.equals(own.get())).isPresent())
                    .forEach(dependency -> events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription())));
            }
        };
    }

    private static Optional<String> featureOf(JavaClass javaClass) {
        var matcher = FEATURE.matcher(javaClass.getName());
        return matcher.find()
            ? Optional.of(matcher.group(1))
            : Optional.empty();
    }

}
