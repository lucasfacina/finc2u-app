package br.com.finc2u.server.features.card.form;

import br.com.finc2u.server.features.card.entity.CardAccount;

import java.time.LocalDateTime;
import java.util.UUID;

public record CardAccountResponse(
        UUID id,
        String cardName,
        Integer closingDate,
        Integer dueDate,
        LocalDateTime createdAt,
        String userName
) {

    public static CardAccountResponse from(CardAccount cardAccount) {
        String userName = cardAccount.getUser() != null
                ? cardAccount.getUser().getName()
                : null;

        return new CardAccountResponse(
                cardAccount.getId(),
                cardAccount.getCardName(),
                cardAccount.getClosingDate(),
                cardAccount.getDueDate(),
                cardAccount.getCreatedAt(),
                userName
        );
    }

}
