package br.com.finc2u.server.features.card.form;

import br.com.finc2u.server.features.card.entity.CardAccount;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

@Schema(description = "Cartão do usuário.")
public record CardAccountResponse(

    @Schema(
        description = "Id do cartão.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    UUID id,

    @Schema(
        description = "Nome do cartão.",
        example = "Nubank"
    )
    String cardName,

    @Schema(
        description = "Dia do mês em que a fatura fecha.",
        example = "9"
    )
    Integer closingDate,

    @Schema(
        description = "Dia do mês em que a fatura vence.",
        example = "15"
    )
    Integer dueDate,

    @Schema(
        description = "Validade do cartão (AAAA-MM); nula se não foi informada.",
        example = "2030-05"
    )
    YearMonth expiration,

    @Schema(
        description = "Momento do cadastro.",
        example = "2026-10-15T09:30:00"
    )
    LocalDateTime createdAt,

    @Schema(
        description = "Nome do usuário dono do cartão.",
        example = "Maria Souza"
    )
    String userName

) {

    public static CardAccountResponse from(CardAccount cardAccount) {
        var userName = cardAccount.getUser() != null
            ? cardAccount.getUser().getName()
            : null;

        return new CardAccountResponse(
            cardAccount.getId(),
            cardAccount.getCardName(),
            cardAccount.getClosingDate(),
            cardAccount.getDueDate(),
            cardAccount.getExpirationDate() != null
                ? YearMonth.from(cardAccount.getExpirationDate())
                : null,
            cardAccount.getCreatedAt(),
            userName
        );
    }

}
