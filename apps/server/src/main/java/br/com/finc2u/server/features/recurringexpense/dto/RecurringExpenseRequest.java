package br.com.finc2u.server.features.recurringexpense.dto;

import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.vo.RecurringExpenseCommand;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

@Schema(description = "Dados para cadastrar ou alterar uma despesa recorrente (modelo mensal).")
public record RecurringExpenseRequest(

    @NotBlank(message = "Descrição é obrigatória!")
    @Schema(
        description = "Descrição da despesa.",
        example = "Netflix"
    )
    String description,

    @NotNull(message = "Valor é obrigatório!")
    @Positive(message = "Valor deve ser positivo.")
    @Schema(
        description = "Valor cobrado a cada mês. Deve ser positivo.",
        example = "39.90"
    )
    BigDecimal value,

    @NotNull(message = "O dia do mês é obrigatório!")
    @Min(value = 1, message = "O dia do mês deve ser no mínimo 1.")
    @Max(value = 31, message = "O dia do mês deve ser no máximo 31.")
    @Schema(
        description = "Dia do mês da cobrança (1 a 31). Em mês mais curto, vence no último dia do mês.",
        example = "5"
    )
    Integer dayOfMonth,

    @NotNull(message = "O mês de início é obrigatório!")
    @Schema(
        description = "Primeiro mês em que a despesa se repete (AAAA-MM).",
        example = "2026-10"
    )
    YearMonth startMonth,

    @Schema(
        description = "Último mês em que a despesa se repete (AAAA-MM). Omitido, não tem término.",
        example = "2027-09"
    )
    YearMonth endMonth,

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
        description = "Id do cartão, opcional. Sem cartão, a despesa é avulsa (aluguel, condomínio).",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
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
