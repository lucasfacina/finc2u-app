package br.com.finc2u.server.features.summary.vo;

import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.expense.entity.Expense;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;

/*
 * O cálculo do resumo mensal, puro: só números entram e saem (sem Spring, banco ou relógio), então o mesmo MonthlySummaryInput
 * produz sempre o mesmo MonthlySummaryFigures. Quem lê os dados e grava o resultado é o MonthlySummaryService.
 * -> baseSalary      = salário vigente no período (zero antes da primeira vigência)
 * -> totalOverdue    = soma das despesas PENDING com vencimento antes da data de referência (atraso derivado)
 * -> totalFixed      = soma das despesas do tipo FIXED, pagas ou não
 * -> cashBalance     = remainingAmount do mês anterior (carryover automático); zero se não houver
 * -> previousMonthMissing = true quando não há resumo do mês anterior: o cashBalance zero vem dessa falta (o cálculo
 *                    não inventa histórico), e o cliente fica sabendo. Vale também para o primeiro mês do usuário
 * -> totalIncome     = baseSalary + totalExtras + cashBalance
 * -> remainingAmount = totalIncome − totalExpenses (quanto sobra ao fim do mês, descontando pago + pendente)
 * -> savingsYield    = savingsBalance atual − savingsBalance do mês anterior; zero se não houver anterior
 * -> savingsBalance  = saldo da poupança do usuário no momento do cálculo (snapshot)
 * -> categoryTotals  = quanto foi gasto em cada categoria (pago + pendente), ordenado pelo nome da categoria
 */
public final class MonthlySummaryCalculator {

    private MonthlySummaryCalculator() {
    }

    public static MonthlySummaryFigures calculate(MonthlySummaryInput input) {
        var totals = MonthlySummaryTotals.from(input.expenses(), input.extraIncomes());

        var previousMonthMissing = input.previousRemainingAmount() == null;
        var cashBalance = previousMonthMissing
            ? BigDecimal.ZERO
            : input.previousRemainingAmount();

        var savingsYield = input.previousSavingsBalance() != null
            ? input.savingsBalance().subtract(input.previousSavingsBalance())
            : BigDecimal.ZERO;

        var totalOverdue = input.expenses().stream()
            .filter(expense -> expense.isOverdue(input.referenceDate()))
            .map(Expense::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var totalIncome = input.baseSalary().add(totals.totalExtras()).add(cashBalance);

        // Por categoria, na ordem do nome: a mesma entrada sempre dá a mesma lista.
        var totalByCategory = new LinkedHashMap<Category, BigDecimal>();
        input.expenses().forEach(expense -> totalByCategory.merge(expense.getCategory(), expense.getValue(), BigDecimal::add));
        var categoryTotals = totalByCategory.entrySet().stream()
            .map(entry -> new CategoryTotal(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing((CategoryTotal total) -> total.category().getName()))
            .toList();

        return new MonthlySummaryFigures(
            input.baseSalary(),
            totalIncome,
            totals.totalExpenses(),
            totals.totalPaid(),
            totals.totalPending(),
            totalOverdue,
            totals.totalFixed(),
            totals.totalExtras(),
            cashBalance,
            previousMonthMissing,
            input.savingsBalance(),
            savingsYield,
            totalIncome.subtract(totals.totalExpenses()),
            categoryTotals
        );
    }

}
