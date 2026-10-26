package br.com.finc2u.server.features.summary.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Os números de um resumo mensal, prontos para a entidade gravar. É o resultado do MonthlySummaryCalculator.
public record MonthlySummaryFigures(
    BigDecimal baseSalary,
    BigDecimal totalIncome,
    BigDecimal totalExpenses,
    BigDecimal totalPaid,
    BigDecimal totalPending,
    BigDecimal totalOverdue,
    BigDecimal totalFixed,
    BigDecimal totalExtras,
    BigDecimal cashBalance,
    boolean previousMonthMissing,
    BigDecimal savingsBalance,
    BigDecimal savingsYield,
    BigDecimal remainingAmount,
    LocalDate referenceDate,
    List<CategoryTotal> categoryTotals
) {
}
