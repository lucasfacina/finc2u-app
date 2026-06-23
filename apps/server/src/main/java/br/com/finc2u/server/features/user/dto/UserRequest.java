package br.com.finc2u.server.features.user.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UserRequest(
        @NotBlank(message = "Nome é obrigatório")
        String name,

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email,

        @NotNull(message = "Salário base é obrigatório")
        @Positive(message = "Salário base deve ser um valor positivo")
        BigDecimal baseSalary,

        @PositiveOrZero(message = "Saldo de poupança não pode ser negativo")
        BigDecimal savingsBalance
) {
}
