package br.com.finc2u.server.features.salaryhistory.form;

import br.com.finc2u.server.features.salaryhistory.entity.SalaryHistory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record SalaryHistoryResponse(
        UUID id,
        UUID userId,
        BigDecimal baseSalary,
        LocalDate effectiveFrom,
        LocalDateTime createdAt
) {

    public static SalaryHistoryResponse from(SalaryHistory history) {
        return new SalaryHistoryResponse(
                history.getId(),
                history.getUser().getId(),
                history.getBaseSalary(),
                history.getEffectiveFrom(),
                history.getCreatedAt()
        );
    }

}
