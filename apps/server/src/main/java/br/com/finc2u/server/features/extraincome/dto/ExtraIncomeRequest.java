package br.com.finc2u.server.features.extraincome.dto;

import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExtraIncomeRequest(
        @NotBlank(message = "Descrição é obrigatória!")
        String description,

        @NotNull(message = "Valor é obrigatório!")
        @Positive(message = "Valor deve ser positivo.")
        BigDecimal value,

        @NotNull(message = "Data é obrigatória!")
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
