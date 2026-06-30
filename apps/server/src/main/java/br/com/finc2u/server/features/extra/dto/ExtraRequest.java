package br.com.finc2u.server.features.extra.dto;

import br.com.finc2u.server.features.extra.entity.Extra;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExtraRequest(
        @NotBlank(message = "Descrição é obrigatória!")
        String description,

        @NotNull(message = "Valor é obrigatório!")
        @Positive(message = "Valor deve ser positivo.")
        BigDecimal value,

        @NotNull(message = "Data é obrigatória!")
        LocalDate date
) {

    public Extra toEntity() {
        return Extra.builder()
                .description(this.description())
                .value(this.value())
                .date(this.date())
                .build();
    }

}
