package br.com.finc2u.server.features.salaryhistory.dto;

import br.com.finc2u.server.features.salaryhistory.entity.SalaryHistory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SalaryHistoryRequest(
        @NotNull(message = "O ID do usuário é obrigatório!")
        UUID userId,

        @NotNull(message = "O salário base é obrigatório!")
        @Positive(message = "O salário base deve ser positivo.")
        BigDecimal baseSalary,

        @NotNull(message = "O campo 'efetivo a partir de' é obrigatório!")
        LocalDate effectiveFrom
) {

    public SalaryHistory toEntity() {
        return SalaryHistory.builder()
                .baseSalary(this.baseSalary())
                .effectiveFrom(this.effectiveFrom())
                .build();
    }

}
