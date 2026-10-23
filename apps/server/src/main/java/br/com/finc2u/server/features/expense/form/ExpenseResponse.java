package br.com.finc2u.server.features.expense.form;

import br.com.finc2u.server.features.category.form.CategoryResponse;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Despesa do usuário. Em compra parcelada, cada parcela é uma despesa.")
public record ExpenseResponse(

    @Schema(
        description = "Id da despesa.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    UUID id,

    @Schema(
        description = "Descrição da despesa.",
        example = "Mercado"
    )
    String description,

    @Schema(
        description = "Valor da despesa (o da parcela, se for parcelada).",
        example = "350.00"
    )
    BigDecimal value,

    @Schema(
        description = "Vencimento (AAAA-MM-DD).",
        example = "2026-10-15"
    )
    LocalDate dueDate,

    @Schema(
        description = "Momento do cadastro.",
        example = "2026-10-15T09:30:00"
    )
    LocalDateTime createdAt,

    @Schema(description = "Tipo da despesa: FIXED (fixa), VARIABLE (variável) ou PARCELED (parcelada).")
    ExpenseType expenseType,

    @Schema(description = "Status de pagamento: PENDING (pendente) ou PAID (paga).")
    PaymentStatus paymentStatus,

    @Schema(
        description = "true quando a despesa está PENDING com vencimento anterior a hoje. É derivado da data de hoje, não é gravado.",
        example = "false"
    )
    boolean overdue,

    @Schema(
        description = "Data do pagamento, quando paga (pode ser desconhecida).",
        example = "2026-10-14"
    )
    LocalDate paidAt,

    @Schema(
        description = "Parcela atual, em compra parcelada.",
        example = "4"
    )
    Integer currentInstallment,

    @Schema(
        description = "Total de parcelas, em compra parcelada.",
        example = "10"
    )
    Integer totalInstallment,

    @Schema(
        description = "Id do grupo que liga as parcelas da mesma compra.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    UUID installmentGroupId,

    @Schema(
        description = "Nome do cartão; nulo em despesa avulsa.",
        example = "Nubank"
    )
    String cardName,

    @Schema(description = "Categoria da despesa.")
    CategoryResponse category

) {

    // referenceDate é "hoje" (vem do Clock do controller): o atraso é derivado dela, não lido do relógio aqui.
    public static ExpenseResponse from(Expense expense, LocalDate referenceDate) {
        return new ExpenseResponse(
            expense.getId(),
            expense.getDescription(),
            expense.getValue(),
            expense.getDueDate(),
            expense.getCreatedAt(),
            expense.getExpenseType(),
            expense.getPaymentStatus(),
            expense.isOverdue(referenceDate),
            expense.getPaidAt(),
            expense.getCurrentInstallment(),
            expense.getTotalInstallment(),
            expense.getInstallmentGroupId(),
            expense.getCardAccount() != null
                ? expense.getCardAccount().getCardName()
                : null,
            CategoryResponse.from(expense.getCategory())
        );
    }

}
