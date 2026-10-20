package br.com.finc2u.server.features.card.dto;

import br.com.finc2u.server.features.card.entity.CardAccount;
import jakarta.validation.constraints.*;

import java.time.YearMonth;

public record CardAccountRequest(
    @NotBlank(message = "O nome do cartão é obrigatório!")
    String cardName,

    @NotNull(message = "O dia de fechamento é obrigatório!")
    @Min(value = 1, message = "O dia de fechamento deve ser no mínimo 1.")
    @Max(value = 31, message = "O dia de fechamento deve ser no máximo 31.")
    Integer closingDate,

    @NotNull(message = "O dia de vencimento é obrigatório!")
    @Min(value = 1, message = "O dia de vencimento deve ser no mínimo 1.")
    @Max(value = 31, message = "O dia de vencimento deve ser no máximo 31.")
    Integer dueDate,

    // Opcional: validade do cartão no formato "2030-05" (mês/ano); a compra depois dela é recusada.
    @FutureOrPresent(message = "A validade do cartão não pode estar no passado.")
    YearMonth expiration
) {

    public CardAccount toEntity() {
        return CardAccount.of(
            this.cardName(),
            this.closingDate(),
            this.dueDate(),
            this.expiration()
        );
    }

}
