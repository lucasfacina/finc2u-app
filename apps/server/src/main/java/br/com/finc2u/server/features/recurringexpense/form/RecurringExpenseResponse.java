package br.com.finc2u.server.features.recurringexpense.form;

import br.com.finc2u.server.features.category.form.CategoryResponse;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

@Schema(description = "Despesa recorrente (modelo mensal).")
public record RecurringExpenseResponse(

    @Schema(
        description = "Id da despesa recorrente.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    UUID id,

    @Schema(
        description = "Descrição da despesa.",
        example = "Netflix"
    )
    String description,

    @Schema(
        description = "Valor cobrado a cada mês.",
        example = "39.90"
    )
    BigDecimal value,

    @Schema(
        description = "Dia do mês da cobrança.",
        example = "5"
    )
    Integer dayOfMonth,

    @Schema(
        description = "Primeiro mês em que se repete (AAAA-MM).",
        example = "2026-10"
    )
    YearMonth startMonth,

    @Schema(
        description = "Último mês em que se repete (AAAA-MM); nulo se não tem término.",
        example = "2027-09"
    )
    YearMonth endMonth,

    @Schema(
        description = "Momento do cadastro.",
        example = "2026-10-15T09:30:00"
    )
    LocalDateTime createdAt,

    @Schema(
        description = "Nome do cartão; nulo em despesa avulsa.",
        example = "Nubank"
    )
    String cardName,

    @Schema(description = "Categoria da despesa.")
    CategoryResponse category

) {

    public static RecurringExpenseResponse from(RecurringExpense recurringExpense) {
        return new RecurringExpenseResponse(
            recurringExpense.getId(),
            recurringExpense.getDescription(),
            recurringExpense.getValue(),
            recurringExpense.getDayOfMonth(),
            YearMonth.from(recurringExpense.getStartMonth()),
            recurringExpense.getEndMonth() != null
                ? YearMonth.from(recurringExpense.getEndMonth())
                : null,
            recurringExpense.getCreatedAt(),
            recurringExpense.getCardAccount() != null
                ? recurringExpense.getCardAccount().getCardName()
                : null,
            CategoryResponse.from(recurringExpense.getCategory())
        );
    }

}
