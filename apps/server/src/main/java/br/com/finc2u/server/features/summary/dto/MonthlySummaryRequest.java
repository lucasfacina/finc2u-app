package br.com.finc2u.server.features.summary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MonthlySummaryRequest(
        @NotNull(message = "O ID do usuário é obrigatório!")
        UUID userId,

        @NotNull(message = "O mês é obrigatório!")
        @Min(value = 1, message = "O mês deve ser um valor entre 1 e 12.")
        @Max(value = 12, message = "O mês deve ser um valor entre 1 e 12.")
        Integer month,

        @NotNull(message = "O ano é obrigatório!")
        @Min(value = 2000, message = "O ano deve ser no mínimo 2000.")
        Integer year
) {
}
