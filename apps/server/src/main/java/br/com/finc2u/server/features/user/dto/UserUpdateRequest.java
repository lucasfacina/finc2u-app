package br.com.finc2u.server.features.user.dto;

import br.com.finc2u.server.features.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record UserUpdateRequest(
    @NotBlank(message = "Nome é obrigatório!")
    String name,

    @NotBlank(message = "E-mail é obrigatório!")
    @Email(message = "E-mail inválido.")
    String email,

    @PositiveOrZero(message = "O saldo da poupança não pode ser negativo.")
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
