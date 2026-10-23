package br.com.finc2u.server.features.user.dto;

import br.com.finc2u.server.features.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

@Schema(description = "Dados para alterar um usuário. O salário não muda aqui: use o histórico de salários.")
public record UserUpdateRequest(

    @NotBlank(message = "Nome é obrigatório!")
    @Schema(
        description = "Nome do usuário.",
        example = "Maria Souza"
    )
    String name,

    @NotBlank(message = "E-mail é obrigatório!")
    @Email(message = "E-mail inválido.")
    @Schema(
        description = "E-mail do usuário. Único.",
        example = "maria@exemplo.com"
    )
    String email,

    @PositiveOrZero(message = "O saldo da poupança não pode ser negativo.")
    @Schema(
        description = "Saldo da poupança (zero ou mais), opcional.",
        example = "5000.00"
    )
    BigDecimal savingsBalance

) {

    public User toEntity() {
        return User.builder()
            .name(this.name)
            .email(this.email)
            .savingsBalance(this.savingsBalance)
            .build();
    }

}
