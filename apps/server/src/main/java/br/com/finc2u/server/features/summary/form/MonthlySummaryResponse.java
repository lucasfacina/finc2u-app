package br.com.finc2u.server.features.summary.form;

import br.com.finc2u.server.features.summary.entity.MonthlySummary;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MonthlySummaryResponse(
        UUID userId,
        Integer month,
        Integer year,
        BigDecimal baseSalary,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal totalPaid,
        BigDecimal totalPending,
        BigDecimal totalExtras,
        BigDecimal cashBalance,
        BigDecimal savingsBalance,
        BigDecimal savingsYield,
        BigDecimal remainingAmount,
        LocalDateTime updatedAt
) {

    public static MonthlySummaryResponse from(MonthlySummary summary) {
        return new MonthlySummaryResponse(
                summary.getUser().getId(),
                summary.getMonth(),
                summary.getYear(),
                summary.getBaseSalary(),
                summary.getTotalIncome(),
                summary.getTotalExpenses(),
                summary.getTotalPaid(),
                summary.getTotalPending(),
                summary.getTotalExtras(),
                summary.getCashBalance(),
                summary.getSavingsBalance(),
                summary.getSavingsYield(),
                summary.getRemainingAmount(),
                summary.getUpdatedAt()
        );
    }

}
