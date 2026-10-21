package br.com.finc2u.server.features.summary.form;

import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.entity.MonthlySummaryCategoryTotal;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
    BigDecimal totalOverdue,
    BigDecimal totalFixed,
    BigDecimal totalExtras,
    BigDecimal cashBalance,
    Boolean previousMonthMissing,
    BigDecimal savingsBalance,
    BigDecimal savingsYield,
    BigDecimal remainingAmount,
    List<CategoryTotalResponse> categoryTotals,
    LocalDateTime updatedAt
) {

    public static MonthlySummaryResponse from(MonthlySummary summary, List<MonthlySummaryCategoryTotal> categoryTotals) {
        return new MonthlySummaryResponse(
            summary.getUser().getId(),
            summary.getMonth(),
            summary.getYear(),
            summary.getBaseSalary(),
            summary.getTotalIncome(),
            summary.getTotalExpenses(),
            summary.getTotalPaid(),
            summary.getTotalPending(),
            summary.getTotalOverdue(),
            summary.getTotalFixed(),
            summary.getTotalExtras(),
            summary.getCashBalance(),
            summary.getPreviousMonthMissing(),
            summary.getSavingsBalance(),
            summary.getSavingsYield(),
            summary.getRemainingAmount(),
            categoryTotals.stream().map(CategoryTotalResponse::from).toList(),
            summary.getUpdatedAt()
        );
    }

}
