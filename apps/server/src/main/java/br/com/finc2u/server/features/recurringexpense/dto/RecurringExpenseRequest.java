package br.com.finc2u.server.features.recurringexpense.dto;

import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.vo.RecurringExpenseCommand;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

public record RecurringExpenseRequest(
    @NotBlank(message = "Descrição é obrigatória!")
    String description,

    @NotNull(message = "Valor é obrigatório!")
    @Positive(message = "Valor deve ser positivo.")
    BigDecimal value,

    // Em mês mais curto que o dia informado, a despesa vence no último dia do mês.
    @NotNull(message = "O dia do mês é obrigatório!")
    @Min(value = 1, message = "O dia do mês deve ser no mínimo 1.")
    @Max(value = 31, message = "O dia do mês deve ser no máximo 31.")
    Integer dayOfMonth,

    // Mês/ano no formato "2026-10".
    @NotNull(message = "O mês de início é obrigatório!")
    YearMonth startMonth,

    // Opcional: último mês em que a despesa se repete; ausente = sem término.
    YearMonth endMonth,

    // A categoria vem por id OU por nome (exatamente um dos dois).
    UUID categoryId,

    // Alternativa ao categoryId; resolvido entre as categorias do próprio usuário (sem diferenciar maiúsculas de minúsculas) e criada na
    // hora se ainda não existir
    @Size(max = 60, message = "O nome da categoria deve conter no máximo 60 caracteres.")
    String categoryName,

    // Opcional: despesa recorrente pode ser avulsa (aluguel, condomínio) sem cartão vinculado.
    UUID cardAccountId
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

    // O término não vem antes do início (só dá para comparar quando os dois vêm).
    @JsonIgnore
    @AssertTrue(message = "O mês de término não pode ser anterior ao mês de início.")
    @SuppressWarnings("unused") // chamado pelo Bean Validation
    public boolean isPeriodValid() {
        return startMonth == null || endMonth == null || !endMonth.isBefore(startMonth);
    }

    public RecurringExpense toEntity() {
        return RecurringExpense.of(this.description(), this.value(), this.dayOfMonth(), this.startMonth(), this.endMonth());
    }

    public RecurringExpenseCommand toCommand() {
        return new RecurringExpenseCommand(toEntity(), categoryId(), categoryName(), cardAccountId());
    }

}
