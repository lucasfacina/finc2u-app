package br.com.finc2u.server.features.extraincome.dto;

import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Dados para registrar ou alterar um ganho extra.")
public record ExtraIncomeRequest(

    @NotBlank(message = "Descrição é obrigatória!")
    @Schema(
        description = "Descrição do ganho.",
        example = "Freelance"
    )
    String description,

    @NotNull(message = "Valor é obrigatório!")
    @Positive(message = "Valor deve ser positivo.")
    @Schema(
        description = "Valor do ganho. Deve ser positivo.",
        example = "1200.00"
    )
    BigDecimal value,

    @NotNull(message = "Data é obrigatória!")
    @Schema(
        description = "Data do ganho (AAAA-MM-DD).",
        example = "2026-10-15"
    )
    LocalDate date

) {

    public ExtraIncome toEntity() {
        return ExtraIncome.builder()
            .description(this.description())
            .value(this.value())
            .date(this.date())
            .build();
    }

}
