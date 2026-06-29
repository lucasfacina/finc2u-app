package br.com.finc2u.server.features.salaryhistory.dto;

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
}
