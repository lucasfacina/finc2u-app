package br.com.finc2u.server.features.expense.dto;

import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.filter.ExpenseFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.util.UUID;

/*
 * Os parâmetros de GET /expenses/filters, com a validação do próprio request (400). O service e a Specification recebem o
 * ExpenseFilter, que fica um valor puro, sem Bean Validation.
 */
@Schema(description = "Filtros da listagem de despesas; todos são opcionais.")
public record ExpenseFilterRequest(

    @Min(value = 1, message = "O mês deve ser um valor entre 1 e 12.")
    @Max(value = 12, message = "O mês deve ser um valor entre 1 e 12.")
    @Schema(
        description = "Mês de vencimento (1 a 12). Exige o ano.",
        example = "10"
    )
    Integer month,

    @Min(value = 2000, message = "O ano deve ser no mínimo 2000.")
    @Schema(
        description = "Ano de vencimento (a partir de 2000).",
        example = "2026"
    )
    Integer year,

    @Schema(description = "Status de pagamento: PENDING (pendente) ou PAID (paga).")
    PaymentStatus status,

    @Schema(description = "Tipo da despesa: FIXED (fixa), VARIABLE (variável) ou PARCELED (parcelada).")
    ExpenseType type,

    @Schema(
        description = "Id do cartão.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    UUID cardAccountId,

    @Schema(
        description = "Id da categoria.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    UUID categoryId,

    @Schema(
        description = "Valor mínimo, inclusive. Não pode passar do máximo.",
        example = "10.00"
    )
    BigDecimal minPrice,

    @Schema(
        description = "Valor máximo, inclusive.",
        example = "500.00"
    )
    BigDecimal maxPrice,

    @Schema(
        description = "true devolve só as despesas pendentes já vencidas (atraso derivado); false ou ausente, sem restrição.",
        example = "true"
    )
    Boolean overdue

) {

    // O mês só faz sentido dentro de um ano; sem o ano o período seria ignorado em silêncio.
    @AssertTrue(message = "Informe o ano junto com o mês.")
    @SuppressWarnings("unused") // chamado pelo Bean Validation
    public boolean isYearInformedWithMonth() {
        return month == null || year != null;
    }

    // Faixa de preço coerente; com apenas um dos limites informados não há o que comparar.
    @AssertTrue(message = "O preço mínimo não pode ser maior que o preço máximo.")
    @SuppressWarnings("unused") // chamado pelo Bean Validation
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
    }

    public ExpenseFilter toFilter() {
        return new ExpenseFilter(month, year, status, type, cardAccountId, categoryId, minPrice, maxPrice, overdue);
    }

}
