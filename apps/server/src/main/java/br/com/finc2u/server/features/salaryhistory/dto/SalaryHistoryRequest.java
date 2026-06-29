package br.com.finc2u.server.features.salaryhistory.dto;

import br.com.finc2u.server.features.salaryhistory.entity.SalaryHistory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SalaryHistoryRequest(
        @NotNull
        UUID userId,

        @NotNull
        @Positive
        BigDecimal baseSalary,

        @NotNull
        LocalDate effectiveFrom
) {

    public SalaryHistory toEntity() {
        return SalaryHistory.builder()
                .baseSalary(this.baseSalary())
                .effectiveFrom(this.effectiveFrom())
                .build();
    }

}
