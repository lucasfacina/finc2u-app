package br.com.finc2u.server.features.user.form;

import br.com.finc2u.server.features.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Usuário cadastrado.")
public record UserResponse(

    @Schema(
        description = "Id do usuário.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    UUID id,

    @Schema(
        description = "Nome do usuário.",
        example = "Maria Souza"
    )
    String name,

    @Schema(
        description = "E-mail do usuário.",
        example = "maria@exemplo.com"
    )
    String email,

    @Schema(
        description = "Momento do cadastro.",
        example = "2026-10-15T09:30:00"
    )
    LocalDateTime createdAt,

    @Schema(
        description = "Salário vigente hoje; nulo se o usuário ainda não tem nenhuma vigência iniciada.",
        example = "3000.00"
    )
    BigDecimal baseSalary,

    @Schema(
        description = "Saldo da poupança.",
        example = "5000.00"
    )
    BigDecimal savingsBalance

) {

    public static UserResponse from(User user, BigDecimal currentBaseSalary) {
        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getCreatedAt(),
            currentBaseSalary,
            user.getSavingsBalance()
        );
    }

}
