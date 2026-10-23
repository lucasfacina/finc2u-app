package br.com.finc2u.server.features.salaryhistory.form;

import br.com.finc2u.server.features.salaryhistory.entity.SalaryHistory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Vigência de salário do usuário.")
public record SalaryHistoryResponse(

    @Schema(
        description = "Id da vigência.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    UUID id,

    @Schema(
        description = "Id do usuário dono do salário.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    UUID userId,

    @Schema(
        description = "Salário base.",
        example = "3000.00"
    )
    BigDecimal baseSalary,

    @Schema(
        description = "Data a partir da qual o salário vale (AAAA-MM-DD).",
        example = "2026-01-01"
    )
    LocalDate effectiveFrom,

    @Schema(
        description = "Momento do cadastro.",
        example = "2026-10-15T09:30:00"
    )
    LocalDateTime createdAt

) {

    public static SalaryHistoryResponse from(SalaryHistory history) {
        return new SalaryHistoryResponse(
            history.getId(),
            history.getUser().getId(),
            history.getBaseSalary(),
            history.getEffectiveFrom(),
            history.getCreatedAt()
        );
    }

}
