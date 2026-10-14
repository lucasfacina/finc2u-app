package br.com.finc2u.server.features.extraincome.form;

import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ExtraIncomeResponse(
        UUID id,
        String description,
        BigDecimal value,
        LocalDate date,
        UUID userId,
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
