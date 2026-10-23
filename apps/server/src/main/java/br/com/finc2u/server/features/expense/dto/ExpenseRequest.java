package br.com.finc2u.server.features.expense.dto;

import br.com.finc2u.server.config.BusinessConstants;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.vo.ExpenseCommand;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Dados para lançar ou alterar uma despesa.")
public record ExpenseRequest(

    @NotBlank(message = "Descrição é obrigatória!")
    @Schema(
        description = "Descrição da despesa.",
        example = "Mercado"
    )
    String description,

    @NotNull(message = "Valor é obrigatório!")
    @Positive(message = "Valor deve ser positivo.")
    @Schema(
        description = "Valor da despesa (o da parcela, se for parcelada). Deve ser positivo.",
        example = "350.00"
    )
    BigDecimal value,

    @NotNull(message = "Tipo de despesa é obrigatório!")
    @Schema(description = "Tipo da despesa: FIXED (fixa), VARIABLE (variável) ou PARCELED (parcelada).")
    ExpenseType expenseType,

    @Schema(
        description = "Vencimento (AAAA-MM-DD). Com cartão, pode ser omitido: sai da fatura calculada a partir de purchaseDate. Em despesa avulsa (sem cartão) é obrigatório.",
        example = "2026-10-15"
    )
    LocalDate dueDate,

    @Schema(
        description = "Data da compra, que define em qual fatura (e vencimento) a despesa cai (AAAA-MM-DD). Omitida, vale hoje.",
        example = "2026-10-01"
    )
    LocalDate purchaseDate,

    @Schema(description = "Status de pagamento. Omitido, a despesa nasce PENDING; se o vencimento for anterior a hoje, é obrigatório informar.")
    PaymentStatus paymentStatus,

    @Schema(
        description = "Data real do pagamento (AAAA-MM-DD). Só vale com PAID e não pode ser futura; desconhecida = omitida.",
        example = "2026-10-14"
    )
    LocalDate paidAt,

    @Min(value = BusinessConstants.MIN_INSTALLMENTS, message = "Parcela atual deve ser no mínimo 1.")
    @Max(value = BusinessConstants.MAX_INSTALLMENTS, message = "Parcela atual deve ser no máximo " + BusinessConstants.MAX_INSTALLMENTS + ".")
    @Schema(
        description = "Parcela atual (1 até o total). Só em PARCELED; omitida, começa na 1.",
        example = "1"
    )
    Integer currentInstallment,

    @Min(value = BusinessConstants.MIN_INSTALLMENTS, message = "Total de parcelas deve ser no mínimo 1.")
    @Max(value = BusinessConstants.MAX_INSTALLMENTS, message = "Total de parcelas deve ser no máximo " + BusinessConstants.MAX_INSTALLMENTS + ".")
    @Schema(
        description = "Total de parcelas (1 a 120). Só pode ser informado em PARCELED, e nelas é obrigatório.",
        example = "10"
    )
    Integer totalInstallment,

    @Schema(
        description = "Id da categoria. Informe o categoryId OU o categoryName (exatamente um dos dois).",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    UUID categoryId,

    @Size(max = 60, message = "O nome da categoria deve conter no máximo 60 caracteres.")
    @Schema(
        description = "Nome da categoria (até 60 caracteres), alternativa ao categoryId: resolvido entre as categorias do próprio usuário, sem diferenciar maiúsculas de minúsculas, e criada na hora se ainda não existir.",
        example = "Pets"
    )
    String categoryName,

    @Schema(
        description = "Id do cartão. Informe o cardAccountId OU o cardName; nenhum dos dois = despesa avulsa (boleto, conta de consumo).",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    UUID cardAccountId,

    @Schema(
        description = "Nome do cartão, alternativa ao cardAccountId: resolvido entre os cartões do próprio usuário, sem diferenciar maiúsculas de minúsculas.",
        example = "Nubank"
    )
    String cardName

) {

    // A categoria é obrigatória: por id ou por nome.
    @JsonIgnore
    @AssertTrue(message = "Categoria é obrigatória: informe o categoryId ou o categoryName.")
    @SuppressWarnings("unused") // chamado pelo Bean Validation
    public boolean isCategoryInformed() {
        return categoryId != null || (categoryName != null && !categoryName.isBlank());
    }

    // ...mas não pelos dois (consistência do próprio request).
    @JsonIgnore
    @AssertTrue(message = "Informe a categoria por id ou por nome, não pelos dois.")
    @SuppressWarnings("unused") // chamado pelo Bean Validation
    public boolean isCategoryReferenceValid() {
        return categoryId == null || categoryName == null || categoryName.isBlank();
    }

    // O cartão é informado por id ou por nome, nunca pelos dois (consistência do próprio request).
    @JsonIgnore
    @AssertTrue(message = "Informe o cartão por id ou por nome, não pelos dois.")
    @SuppressWarnings("unused") // chamado pelo Bean Validation
    public boolean isCardReferenceValid() {
        return cardAccountId == null || cardName == null || cardName.isBlank();
    }

    // A parcela atual não passa do total (só dá para comparar quando os dois vêm).
    @JsonIgnore
    @AssertTrue(message = "A parcela atual não pode ser maior que o total de parcelas.")
    @SuppressWarnings("unused") // chamado pelo Bean Validation
    public boolean isInstallmentOrderValid() {
        return currentInstallment == null || totalInstallment == null || currentInstallment <= totalInstallment;
    }

    // Parcelas só fazem sentido em PARCELED. Sem tipo informado, quem reclama é o @NotNull do próprio tipo.
    @JsonIgnore
    @AssertTrue(message = "Parcelas só podem ser informadas em despesas do tipo PARCELED.")
    @SuppressWarnings("unused") // chamado pelo Bean Validation
    public boolean isInstallmentsAllowedForType() {
        return expenseType == null || expenseType == ExpenseType.PARCELED
            || (currentInstallment == null && totalInstallment == null);
    }

    public Expense toEntity() {
        return Expense.builder()
            .description(this.description())
            .value(this.value())
            .dueDate(this.dueDate())
            .expenseType(this.expenseType())
            .paymentStatus(this.paymentStatus())
            .paidAt(this.paidAt())
            .currentInstallment(this.currentInstallment())
            .totalInstallment(this.totalInstallment())
            .build();
    }

    public ExpenseCommand toCommand() {
        return new ExpenseCommand(toEntity(), cardAccountId(), cardName(), categoryId(), categoryName(), purchaseDate());
    }

}
