package br.com.finc2u.server.features.user.dto;

import br.com.finc2u.server.features.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Dados para cadastrar um usuário.")
public record UserRequest(

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

    @NotNull(message = "Salário base é obrigatório!")
    @Positive(message = "Salário base deve ser um valor positivo.")
    @Schema(
        description = "Primeiro salário base. Deve ser positivo; vira a primeira vigência do histórico de salários.",
        example = "3000.00"
    )
    BigDecimal baseSalary,

    @PositiveOrZero(message = "O saldo da poupança não pode ser negativo.")
    @Schema(
        description = "Saldo da poupança (zero ou mais), opcional.",
        example = "5000.00"
    )
    BigDecimal savingsBalance,

    @Schema(
        description = "Início da vigência do primeiro salário (AAAA-MM-DD). Omitido, vale o dia 1 do mês de hoje.",
        example = "2026-01-01"
    )
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
