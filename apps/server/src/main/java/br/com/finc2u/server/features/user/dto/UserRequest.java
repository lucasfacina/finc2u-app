package br.com.finc2u.server.features.user.dto;

import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.entity.UserConfiguration;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UserRequest(
        @NotBlank(message = "Nome é obrigatório!")
        String name,

        @NotBlank(message = "E-mail é obrigatório!")
        @Email(message = "E-mail inválido.")
        String email,

        @NotNull(message = "Salário base é obrigatório!")
        @Positive(message = "Salário base deve ser um valor positivo.")
        BigDecimal baseSalary,

        @PositiveOrZero(message = "O saldo da poupança não pode ser negativo.")
        BigDecimal savingsBalance
) {

    public User toEntity() {
        User user = User.builder()
                .name(this.name)
                .email(this.email)
                .build();
        user.setUserConfiguration(UserConfiguration.builder()
                .baseSalary(this.baseSalary)
                .savingsBalance(this.savingsBalance)
                .build());
        return user;
    }

}
