package br.com.finc2u.server.features.summary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Mês a calcular (ou recalcular) no resumo do usuário.")
public record MonthlySummaryRequest(

    @NotNull(message = "O ID do usuário é obrigatório!")
    @Schema(
        description = "Id do usuário dono do resumo.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    UUID userId,

    @NotNull(message = "O mês é obrigatório!")
    @Min(value = 1, message = "O mês deve ser um valor entre 1 e 12.")
    @Max(value = 12, message = "O mês deve ser um valor entre 1 e 12.")
    @Schema(
        description = "Mês do resumo (1 a 12).",
        example = "10"
    )
    Integer month,

    @NotNull(message = "O ano é obrigatório!")
    @Min(value = 2000, message = "O ano deve ser no mínimo 2000.")
    @Schema(
        description = "Ano do resumo (a partir de 2000).",
        example = "2026"
    )
    Integer year

) {
}
