package br.com.finc2u.server.features.user.dto;

import br.com.finc2u.server.features.user.entity.User;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    BigDecimal savingsBalance,

    LocalDate effectiveFrom
) {

    public User toEntity() {
        return User.builder()
            .name(this.name)
            .email(this.email)
            .savingsBalance(this.savingsBalance)
            .build();
    }

}
