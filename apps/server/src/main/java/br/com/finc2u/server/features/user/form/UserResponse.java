package br.com.finc2u.server.features.user.form;

import br.com.finc2u.server.features.user.entity.User;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder(toBuilder = true)
public record UserResponse(
        UUID id,
        String name,
        String email,
        LocalDateTime createdAt,
        BigDecimal baseSalary,
        BigDecimal savingsBalance
) {

    public static UserResponse from(User user) {
        BigDecimal salary = null;
        BigDecimal savings = null;

        if (user.getUserConfiguration() != null) {
            salary = user.getUserConfiguration().getBaseSalary();
            savings = user.getUserConfiguration().getSavingsBalance();
        }

        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getCreatedAt(),
                salary,
                savings
        );
    }

}