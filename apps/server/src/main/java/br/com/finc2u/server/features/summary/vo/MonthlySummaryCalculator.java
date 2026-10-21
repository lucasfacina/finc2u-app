package br.com.finc2u.server.features.summary.vo;

import java.math.BigDecimal;

/*
 * O cálculo do resumo mensal, puro: só números entram e saem (sem Spring, banco ou relógio), então o mesmo MonthlySummaryInput
 * produz sempre o mesmo MonthlySummaryFigures. Quem lê os dados e grava o resultado é o MonthlySummaryService.
 * -> baseSalary      = salário vigente no período (zero antes da primeira vigência)
 * -> cashBalance     = remainingAmount do mês anterior (carryover automático); zero se não houver
 * -> totalIncome     = baseSalary + totalExtras + cashBalance
 * -> remainingAmount = totalIncome − totalExpenses (quanto sobra ao fim do mês, descontando pago + pendente)
 * -> savingsYield    = savingsBalance atual − savingsBalance do mês anterior; zero se não houver anterior
 * -> savingsBalance  = saldo da poupança do usuário no momento do cálculo (snapshot)
 */
public final class MonthlySummaryCalculator {

    private MonthlySummaryCalculator() {
    }

    public static MonthlySummaryFigures calculate(MonthlySummaryInput input) {
        var totals = MonthlySummaryTotals.from(input.expenses(), input.extraIncomes());

        var cashBalance = input.previousRemainingAmount() != null
            ? input.previousRemainingAmount()
            : BigDecimal.ZERO;

        var savingsYield = input.previousSavingsBalance() != null
            ? input.savingsBalance().subtract(input.previousSavingsBalance())
            : BigDecimal.ZERO;

        var totalIncome = input.baseSalary().add(totals.totalExtras()).add(cashBalance);

        return new MonthlySummaryFigures(
            input.baseSalary(),
            totalIncome,
            totals.totalExpenses(),
            totals.totalPaid(),
            totals.totalPending(),
            totals.totalExtras(),
            cashBalance,
            input.savingsBalance(),
            savingsYield,
            totalIncome.subtract(totals.totalExpenses())
        );
    }

}
