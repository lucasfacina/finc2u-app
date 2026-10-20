package br.com.finc2u.server.features.expense.filter;

import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.util.UUID;

public record ExpenseFilter(
    @Min(value = 1, message = "O mês deve ser um valor entre 1 e 12.")
    @Max(value = 12, message = "O mês deve ser um valor entre 1 e 12.")
    Integer month,

    @Min(value = 2000, message = "O ano deve ser no mínimo 2000.")
    Integer year,

    PaymentStatus status,
    ExpenseType type,
    UUID cardAccountId,
    UUID categoryId,
    BigDecimal minPrice,
    BigDecimal maxPrice
) {

    // Faixa de preço coerente; com apenas um dos limites informados não há o que comparar.
    @AssertTrue(message = "O preço mínimo não pode ser maior que o preço máximo.")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
    }

}
