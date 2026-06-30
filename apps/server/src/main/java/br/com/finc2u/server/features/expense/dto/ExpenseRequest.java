package br.com.finc2u.server.features.expense.dto;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ExpenseRequest(
        @NotBlank(message = "Descrição é obrigatória")
        String description,

        @NotNull(message = "Valor é obrigatório")
        @Positive(message = "Valor deve ser positivo")
        BigDecimal value,

        @NotNull(message = "Tipo de despesa é obrigatório")
        ExpenseType expenseType,

        // Opcional: quando ausente e houver cartão, é calculado a partir de purchaseDate + fechamento/vencimento do cartão.
        // Para despesas avulsas (sem cartão), continua sendo obrigatório (validado no service).
        LocalDate dueDate,

        // Opcional: data da compra, usada para determinar em qual fatura (e vencimento) a despesa cai.
        LocalDate purchaseDate,

        // Opcional: quando ausente, é definido por padrão pela data de vencimento (<= hoje → PAID, futuro → PENDING).
        PaymentStatus paymentStatus,

        Integer currentInstallment,
        Integer totalInstallment,

        // Opcional: despesa pode ser avulsa (boleto, conta de consumo) sem cartão vinculado
        UUID cardAccountId,

        // Opcional: tags associadas à despesa (para fins de categorização/relatórios)
        List<UUID> tagIds
) {

    public Expense toEntity() {
        return Expense.builder()
                .description(this.description())
                .value(this.value())
                .dueDate(this.dueDate())
                .expenseType(this.expenseType())
                .paymentStatus(this.paymentStatus())
                .currentInstallment(this.currentInstallment())
                .totalInstallment(this.totalInstallment())
                .build();
    }

}
