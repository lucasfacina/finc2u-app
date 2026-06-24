package br.com.finc2u.server.features.card.dto;

import br.com.finc2u.server.features.card.entity.CardAccount;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CardAccountRequest(
        @NotBlank(message = "O nome do cartão é obrigatório")
        String cardName,

        @NotNull(message = "O dia de fechamento é obrigatório")
        @Min(value = 1, message = "O dia de fechamento deve ser no mínimo 1")
        @Max(value = 31, message = "O dia de fechamento deve ser no máximo 31")
        Integer closingDate,

        @NotNull(message = "O dia de vencimento é obrigatório")
        @Min(value = 1, message = "O dia de vencimento deve ser no mínimo 1")
        @Max(value = 31, message = "O dia de vencimento deve ser no máximo 31")
        Integer dueDate
) {

    public CardAccount toEntity() {
        return CardAccount.builder()
                .cardName(this.cardName())
                .closingDate(this.closingDate())
                .dueDate(this.dueDate())
                .build();
    }

}
