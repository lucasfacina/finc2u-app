package br.com.finc2u.server.features.salaryhistory.dto;

import br.com.finc2u.server.features.salaryhistory.entity.SalaryHistory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Dados para registrar ou alterar uma vigência de salário.")
public record SalaryHistoryRequest(

    @NotNull(message = "O ID do usuário é obrigatório!")
    @Schema(
        description = "Id do usuário dono do salário.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    UUID userId,

    @NotNull(message = "O salário base é obrigatório!")
    @Positive(message = "O salário base deve ser positivo.")
    @Schema(
        description = "Salário base. Deve ser positivo.",
        example = "3000.00"
    )
    BigDecimal baseSalary,

    @NotNull(message = "O campo 'efetivo a partir de' é obrigatório!")
    @Schema(
        description = "Data a partir da qual o salário vale (AAAA-MM-DD).",
        example = "2026-01-01"
    )
    LocalDate effectiveFrom

) {

    public SalaryHistory toEntity() {
        return SalaryHistory.builder()
            .baseSalary(this.baseSalary())
            .effectiveFrom(this.effectiveFrom())
            .build();
    }

}
