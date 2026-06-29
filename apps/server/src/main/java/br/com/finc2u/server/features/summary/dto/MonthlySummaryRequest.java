package br.com.finc2u.server.features.summary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MonthlySummaryRequest(
        @NotNull(message = "O ID do usuário é obrigatório")
        UUID userId,

        @NotNull(message = "O mês é obrigatório")
        @Min(1) @Max(12)
        Integer month,

        @NotNull(message = "O ano é obrigatório")
        @Min(2000)
        Integer year
) {
}
