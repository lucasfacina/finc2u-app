package br.com.finc2u.server.features.extraincome.form;

import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Ganho extra do usuário.")
public record ExtraIncomeResponse(

    @Schema(
        description = "Id do ganho extra.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    UUID id,

    @Schema(
        description = "Descrição do ganho.",
        example = "Freelance"
    )
    String description,

    @Schema(
        description = "Valor do ganho.",
        example = "1200.00"
    )
    BigDecimal value,

    @Schema(
        description = "Data do ganho (AAAA-MM-DD).",
        example = "2026-10-15"
    )
    LocalDate date,

    @Schema(
        description = "Id do usuário dono do ganho.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    UUID userId,

    @Schema(
        description = "Momento do cadastro.",
        example = "2026-10-15T09:30:00"
    )
    LocalDateTime createdAt

) {

    public static ExtraIncomeResponse from(ExtraIncome extraIncome) {
        return new ExtraIncomeResponse(
            extraIncome.getId(),
            extraIncome.getDescription(),
            extraIncome.getValue(),
            extraIncome.getDate(),
            extraIncome.getUser().getId(),
            extraIncome.getCreatedAt()
        );
    }

}
