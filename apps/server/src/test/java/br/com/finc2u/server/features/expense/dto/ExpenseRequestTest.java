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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private Set<ConstraintViolation<ExpenseRequest>> validate(ExpenseType type, Integer currentInstallment,
                                                              Integer totalInstallment) {
        var request = new ExpenseRequest(
            "Compra", BigDecimal.valueOf(100), type, LocalDate.of(2026, 6, 20), null,
            PaymentStatus.PENDING, null, currentInstallment, totalInstallment, UUID.randomUUID(), null, null
        );
        return VALIDATOR.validate(request);
    }

    private String messageOf(Set<ConstraintViolation<ExpenseRequest>> violations) {
        return violations.iterator().next().getMessage();
    }

}
