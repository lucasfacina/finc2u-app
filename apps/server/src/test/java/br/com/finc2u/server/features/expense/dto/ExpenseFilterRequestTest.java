package br.com.finc2u.server.features.expense.dto;

import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.filter.ExpenseFilter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpenseFilterRequestTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @Test
    void shouldBeValid_whenNoCriterionIsInformed() {
        assertTrue(validate(request(null, null, null, null)).isEmpty());
    }

    @Test
    void shouldBeValid_whenTheMonthComesWithTheYear() {
        assertTrue(validate(request(6, 2026, null, null)).isEmpty());
    }

    @Test
    void shouldBeValid_whenOnlyTheYearIsInformed() {
        assertTrue(validate(request(null, 2026, null, null)).isEmpty());
    }

    @Test
    void shouldBeInvalid_whenTheMonthIsOutOfRange() {
        assertEquals("O mês deve ser um valor entre 1 e 12.", onlyMessage(request(0, 2026, null, null)));
        assertEquals("O mês deve ser um valor entre 1 e 12.", onlyMessage(request(13, 2026, null, null)));
    }

    @Test
    void shouldBeInvalid_whenTheYearIsBefore2000() {
        assertEquals("O ano deve ser no mínimo 2000.", onlyMessage(request(6, 1999, null, null)));
    }

    // Sem o ano o período seria ignorado em silêncio: vira 400.
    @Test
    void shouldBeInvalid_whenTheMonthComesWithoutTheYear() {
        assertEquals("Informe o ano junto com o mês.", onlyMessage(request(6, null, null, null)));
    }

    @Test
    void shouldBeInvalid_whenTheMinimumPriceIsGreaterThanTheMaximum() {
        assertEquals("O preço mínimo não pode ser maior que o preço máximo.",
            onlyMessage(request(null, null, BigDecimal.valueOf(200), BigDecimal.valueOf(100))));
    }

    @Test
    void shouldBeValid_whenThePriceRangeIsCoherentOrOpen() {
        assertTrue(validate(request(null, null, BigDecimal.valueOf(100), BigDecimal.valueOf(100))).isEmpty());
        assertTrue(validate(request(null, null, BigDecimal.valueOf(100), null)).isEmpty());
        assertTrue(validate(request(null, null, null, BigDecimal.valueOf(100))).isEmpty());
    }

    @Test
    void toFilter_shouldCarryEveryCriterionToThePureFilter() {
        var cardId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var request = new ExpenseFilterRequest(6, 2026, PaymentStatus.PENDING, ExpenseType.FIXED, cardId, categoryId,
            BigDecimal.valueOf(10), BigDecimal.valueOf(500), true);

        var filter = request.toFilter();

        assertEquals(new ExpenseFilter(6, 2026, PaymentStatus.PENDING, ExpenseType.FIXED, cardId, categoryId,
            BigDecimal.valueOf(10), BigDecimal.valueOf(500), true), filter);
    }

    private ExpenseFilterRequest request(Integer month, Integer year, BigDecimal minPrice, BigDecimal maxPrice) {
        return new ExpenseFilterRequest(month, year, null, null, null, null, minPrice, maxPrice, null);
    }

    private Set<ConstraintViolation<ExpenseFilterRequest>> validate(ExpenseFilterRequest request) {
        return VALIDATOR.validate(request);
    }

    private String onlyMessage(ExpenseFilterRequest request) {
        var violations = validate(request);
        assertEquals(1, violations.size(), violations.toString());
        return violations.iterator().next().getMessage();
    }

}
