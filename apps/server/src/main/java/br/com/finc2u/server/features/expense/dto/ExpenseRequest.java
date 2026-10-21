package br.com.finc2u.server.features.expense.dto;

import br.com.finc2u.server.config.BusinessConstants;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.vo.ExpenseCommand;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseRequest(
    @NotBlank(message = "Descrição é obrigatória!")
    String description,

    @NotNull(message = "Valor é obrigatório!")
    @Positive(message = "Valor deve ser positivo.")
    BigDecimal value,

    @NotNull(message = "Tipo de despesa é obrigatório!")
    ExpenseType expenseType,

    // Opcional: quando ausente e houver cartão, é calculado a partir de purchaseDate + fechamento/vencimento do cartão.
    // Para despesas avulsas (sem cartão), continua sendo obrigatório (validado no service).
    LocalDate dueDate,

    // Opcional: data da compra, usada para determinar em qual fatura (e vencimento) a despesa cai.
    LocalDate purchaseDate,

    // Opcional: quando ausente, a despesa nasce PENDING. Com vencimento anterior a hoje, é obrigatório informar (400).
    PaymentStatus paymentStatus,

    // Opcional: data real do pagamento. Só vale com PAID e não pode ser futura (422); desconhecida = ausente.
    LocalDate paidAt,

    // Opcionais (só valem para PARCELED). Com valor informado, ficam entre 1 e o teto de parcelas; a relação
    // parcela atual <= total é a guarda da entidade (422).
    @Min(value = BusinessConstants.MIN_INSTALLMENTS, message = "Parcela atual deve ser no mínimo 1.")
    @Max(value = BusinessConstants.MAX_INSTALLMENTS, message = "Parcela atual deve ser no máximo " + BusinessConstants.MAX_INSTALLMENTS + ".")
    Integer currentInstallment,

    @Min(value = BusinessConstants.MIN_INSTALLMENTS, message = "Total de parcelas deve ser no mínimo 1.")
    @Max(value = BusinessConstants.MAX_INSTALLMENTS, message = "Total de parcelas deve ser no máximo " + BusinessConstants.MAX_INSTALLMENTS + ".")
    Integer totalInstallment,

    @NotNull(message = "Categoria é obrigatória!")
    UUID categoryId,

    // Opcional: despesa pode ser avulsa (boleto, conta de consumo) sem cartão vinculado
    UUID cardAccountId,

    // Opcional: alternativa ao cardAccountId; resolvido entre os cartões do próprio usuário (sem diferenciar maiúsculas de minúsculas)
    String cardName
) {

    // O cartão é informado por id ou por nome, nunca pelos dois (consistência do próprio request).
    @JsonIgnore
    @AssertTrue(message = "Informe o cartão por id ou por nome, não pelos dois.")
    @SuppressWarnings("unused") // chamado pelo Bean Validation
    public boolean isCardReferenceValid() {
        return cardAccountId == null || cardName == null || cardName.isBlank();
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
        return new ExpenseCommand(toEntity(), cardAccountId(), cardName(), categoryId(), purchaseDate());
    }

}
