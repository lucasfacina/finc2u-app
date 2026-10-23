package br.com.finc2u.server.features.card.dto;

import br.com.finc2u.server.features.card.entity.CardAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.YearMonth;

@Schema(description = "Dados para cadastrar ou alterar um cartão.")
public record CardAccountRequest(

    @NotBlank(message = "O nome do cartão é obrigatório!")
    @Schema(
        description = "Nome do cartão. Único por usuário, sem diferenciar maiúsculas de minúsculas.",
        example = "Nubank"
    )
    String cardName,

    @NotNull(message = "O dia de fechamento é obrigatório!")
    @Min(value = 1, message = "O dia de fechamento deve ser no mínimo 1.")
    @Max(value = 31, message = "O dia de fechamento deve ser no máximo 31.")
    @Schema(
        description = "Dia do mês em que a fatura fecha (1 a 31).",
        example = "9"
    )
    Integer closingDate,

    @NotNull(message = "O dia de vencimento é obrigatório!")
    @Min(value = 1, message = "O dia de vencimento deve ser no mínimo 1.")
    @Max(value = 31, message = "O dia de vencimento deve ser no máximo 31.")
    @Schema(
        description = "Dia do mês em que a fatura vence (1 a 31).",
        example = "15"
    )
    Integer dueDate,

    @FutureOrPresent(message = "A validade do cartão não pode estar no passado.")
    @Schema(
        description = "Validade do cartão (AAAA-MM), opcional. Compra depois dela é recusada.",
        example = "2030-05"
    )
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
