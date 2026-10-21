package br.com.finc2u.server.features.summary.vo;

import java.math.BigDecimal;

// Os números de um resumo mensal, prontos para a entidade gravar. É o resultado do MonthlySummaryCalculator.
public record MonthlySummaryFigures(
    BigDecimal baseSalary,
    BigDecimal totalIncome,
    BigDecimal totalExpenses,
    BigDecimal totalPaid,
    BigDecimal totalPending,
    BigDecimal totalExtras,
    BigDecimal cashBalance,
    BigDecimal savingsBalance,
    BigDecimal savingsYield,
    BigDecimal remainingAmount
) {
}
