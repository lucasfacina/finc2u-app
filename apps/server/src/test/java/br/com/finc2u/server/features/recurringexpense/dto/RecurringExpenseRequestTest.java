package br.com.finc2u.server.features.recurringexpense.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecurringExpenseRequestTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @Test
    void shouldBeValid_whenTheCategoryComesOnlyByName() {
        assertTrue(validateCategory(null, "Pets").isEmpty());
    }

    @Test
    void shouldBeValid_whenTheCategoryComesOnlyById() {
        assertTrue(validateCategory(UUID.randomUUID(), null).isEmpty());
    }

    @Test
    void shouldTreatABlankCategoryNameAsAbsent_whenTheIdComes() {
        assertTrue(validateCategory(UUID.randomUUID(), "   ").isEmpty());
    }

    @Test
    void shouldBeInvalid_whenNoCategoryIsInformed() {
        var violations = validateCategory(null, null);

        assertEquals(1, violations.size());
        assertEquals("categoryInformed", pathOf(violations));
        assertEquals("Categoria é obrigatória: informe o categoryId ou o categoryName.", messageOf(violations));
    }

    @Test
    void shouldBeInvalid_whenTheCategoryNameIsOnlyBlank() {
        var violations = validateCategory(null, "  ");

        assertEquals(1, violations.size());
        assertEquals("categoryInformed", pathOf(violations));
    }

    @Test
    void shouldBeInvalid_whenTheCategoryComesByIdAndByName() {
        var violations = validateCategory(UUID.randomUUID(), "Pets");

        assertEquals(1, violations.size());
        assertEquals("categoryReferenceValid", pathOf(violations));
        assertEquals("Informe a categoria por id ou por nome, não pelos dois.", messageOf(violations));
    }

    @Test
    void shouldBeInvalid_whenTheCategoryNameIsLongerThan60Characters() {
        var violations = validateCategory(null, "x".repeat(61));

        assertEquals(1, violations.size());
        assertEquals("O nome da categoria deve conter no máximo 60 caracteres.", messageOf(violations));
    }

    @Test
    void shouldBeInvalid_whenTheEndMonthIsBeforeTheStartMonth() {
        var violations = VALIDATOR.validate(request(YearMonth.of(2026, 10), YearMonth.of(2026, 9), null, "Pets"));

        assertEquals(1, violations.size());
        assertEquals("periodValid", pathOf(violations));
        assertEquals("O mês de término não pode ser anterior ao mês de início.", messageOf(violations));
    }

    @Test
    void shouldBeValid_whenTheEndMonthEqualsTheStartMonthOrIsAbsent() {
        assertTrue(VALIDATOR.validate(request(YearMonth.of(2026, 10), YearMonth.of(2026, 10), null, "Pets")).isEmpty());
        assertTrue(VALIDATOR.validate(request(YearMonth.of(2026, 10), null, null, "Pets")).isEmpty());
    }

    private Set<ConstraintViolation<RecurringExpenseRequest>> validateCategory(UUID categoryId, String categoryName) {
        return VALIDATOR.validate(request(YearMonth.of(2026, 10), null, categoryId, categoryName));
    }

    private RecurringExpenseRequest request(YearMonth startMonth, YearMonth endMonth, UUID categoryId, String categoryName) {
        return new RecurringExpenseRequest("Spotify", BigDecimal.valueOf(21.90), 5, startMonth, endMonth, categoryId, categoryName, null);
    }

    private String pathOf(Set<ConstraintViolation<RecurringExpenseRequest>> violations) {
        return violations.iterator().next().getPropertyPath().toString();
    }

    private String messageOf(Set<ConstraintViolation<RecurringExpenseRequest>> violations) {
        return violations.iterator().next().getMessage();
    }

}
