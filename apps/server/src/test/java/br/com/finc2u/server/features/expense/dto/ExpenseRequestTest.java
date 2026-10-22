package br.com.finc2u.server.features.expense.dto;

import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExpenseRequestTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @Test
    void shouldBeValid_whenParceledWithCurrentBeforeTotal() {
        assertTrue(validate(ExpenseType.PARCELED, 2, 5).isEmpty());
    }

    @Test
    void shouldBeValid_whenCurrentEqualsTotal() {
        assertTrue(validate(ExpenseType.PARCELED, 5, 5).isEmpty());
    }

    @Test
    void shouldBeValid_whenNonParceledHasNoInstallments() {
        assertTrue(validate(ExpenseType.VARIABLE, null, null).isEmpty());
    }

    @Test
    void shouldBeInvalid_whenCurrentInstallmentIsGreaterThanTotal() {
        var violations = validate(ExpenseType.PARCELED, 6, 5);

        assertEquals(1, violations.size());
        assertEquals("A parcela atual não pode ser maior que o total de parcelas.", messageOf(violations));
    }

    @Test
    void shouldBeInvalid_whenInstallmentsAreInformedForNonParceledType() {
        var violations = validate(ExpenseType.VARIABLE, null, 3);

        assertEquals(1, violations.size());
        assertEquals("Parcelas só podem ser informadas em despesas do tipo PARCELED.", messageOf(violations));
    }

    @Test
    void shouldBeInvalid_whenOnlyCurrentInstallmentIsInformedForFixedType() {
        var violations = validate(ExpenseType.FIXED, 1, null);

        assertEquals(1, violations.size());
        assertEquals("Parcelas só podem ser informadas em despesas do tipo PARCELED.", messageOf(violations));
    }

    @Test
    void shouldReportOnlyTheMissingType_whenTypeIsNullAndInstallmentsAreInformed() {
        var violations = validate(null, 1, 3);

        assertEquals(1, violations.size());
        assertEquals("Tipo de despesa é obrigatório!", messageOf(violations));
    }

    // ---- categoria: por id ou por nome (exatamente um dos dois) ----

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
        assertEquals("Categoria é obrigatória: informe o categoryId ou o categoryName.", messageOf(violations));
    }

    @Test
    void shouldBeInvalid_whenTheCategoryNameIsOnlyBlank() {
        var violations = validateCategory(null, "  ");

        assertEquals(1, violations.size());
        assertEquals("Categoria é obrigatória: informe o categoryId ou o categoryName.", messageOf(violations));
    }

    @Test
    void shouldBeInvalid_whenTheCategoryComesByIdAndByName() {
        var violations = validateCategory(UUID.randomUUID(), "Pets");

        assertEquals(1, violations.size());
        assertEquals("Informe a categoria por id ou por nome, não pelos dois.", messageOf(violations));
    }

    @Test
    void shouldBeInvalid_whenTheCategoryNameIsLongerThan60Characters() {
        var violations = validateCategory(null, "x".repeat(61));

        assertEquals(1, violations.size());
        assertEquals("O nome da categoria deve conter no máximo 60 caracteres.", messageOf(violations));
    }

    @Test
    void toCommand_shouldCarryTheCategoryName() {
        var request = requestWithCategory(null, "Pets");

        var command = request.toCommand();

        assertEquals("Pets", command.categoryName());
        assertNull(command.categoryId());
    }

    private Set<ConstraintViolation<ExpenseRequest>> validateCategory(UUID categoryId, String categoryName) {
        return VALIDATOR.validate(requestWithCategory(categoryId, categoryName));
    }

    private ExpenseRequest requestWithCategory(UUID categoryId, String categoryName) {
        return new ExpenseRequest(
            "Compra", BigDecimal.valueOf(100), ExpenseType.VARIABLE, LocalDate.of(2026, 6, 20), null,
            PaymentStatus.PENDING, null, null, null, categoryId, categoryName, null, null
        );
    }

    private Set<ConstraintViolation<ExpenseRequest>> validate(ExpenseType type, Integer currentInstallment,
                                                              Integer totalInstallment) {
        var request = new ExpenseRequest(
            "Compra", BigDecimal.valueOf(100), type, LocalDate.of(2026, 6, 20), null,
            PaymentStatus.PENDING, null, currentInstallment, totalInstallment, UUID.randomUUID(), null, null, null
        );
        return VALIDATOR.validate(request);
    }

    private String messageOf(Set<ConstraintViolation<ExpenseRequest>> violations) {
        return violations.iterator().next().getMessage();
    }

}
