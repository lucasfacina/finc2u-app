package br.com.finc2u.server.features.extra.form;

import br.com.finc2u.server.features.extra.entity.Extra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ExtraResponse(
        UUID id,
        String description,
        BigDecimal value,
        LocalDate date,
        UUID userId,
        LocalDateTime createdAt
) {

    public static ExtraResponse from(Extra extra) {
        return new ExtraResponse(
                extra.getId(),
                extra.getDescription(),
                extra.getValue(),
                extra.getDate(),
                extra.getUser().getId(),
                extra.getCreatedAt()
        );
    }

}
