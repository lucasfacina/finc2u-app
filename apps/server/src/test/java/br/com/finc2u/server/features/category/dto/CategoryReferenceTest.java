package br.com.finc2u.server.features.category.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryReferenceTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    // Um request qualquer que só tem a categoria: a regra mora na interface, não nele.
    private record Request(UUID categoryId, String categoryName) implements CategoryReference {
    }

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @Test
    void shouldBeValid_whenTheCategoryComesOnlyByIdOrOnlyByName() {
        assertTrue(VALIDATOR.validate(new Request(UUID.randomUUID(), null)).isEmpty());
        assertTrue(VALIDATOR.validate(new Request(null, "Pets")).isEmpty());
    }

    @Test
    void shouldTreatABlankNameAsAbsent_whenTheIdComes() {
        assertTrue(VALIDATOR.validate(new Request(UUID.randomUUID(), "  ")).isEmpty());
    }

    @Test
    void shouldReportTheCategoryAsRequired_whenNeitherComes() {
        assertSingleViolation(new Request(null, null), "categoryInformed", "Categoria é obrigatória: informe o categoryId ou o categoryName.");
        assertSingleViolation(new Request(null, "  "), "categoryInformed", "Categoria é obrigatória: informe o categoryId ou o categoryName.");
    }

    @Test
    void shouldReportTheCategoryAsAmbiguous_whenBothCome() {
        assertSingleViolation(new Request(UUID.randomUUID(), "Pets"), "categoryReferenceValid", "Informe a categoria por id ou por nome, não pelos dois.");
    }

    // As validações viram propriedades do bean (isCategoryInformed...), mas não podem entrar no JSON do request.
    @Test
    void shouldNotAddThePropertiesOfTheValidationsToTheJson() {
        var json = JsonMapper.builder().build().writeValueAsString(new Request(null, "Pets"));

        assertEquals("{\"categoryId\":null,\"categoryName\":\"Pets\"}", json);
    }

    private void assertSingleViolation(Request request, String path, String message) {
        Set<ConstraintViolation<Request>> violations = VALIDATOR.validate(request);

        assertEquals(1, violations.size());
        assertEquals(path, violations.iterator().next().getPropertyPath().toString());
        assertEquals(message, violations.iterator().next().getMessage());
    }

}
