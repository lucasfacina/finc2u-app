package br.com.finc2u.server.features.summary.vo;

import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.expense.entity.Expense;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;

/**
 * O cálculo do resumo mensal, puro: só números entram e saem (sem Spring, banco ou relógio), então o mesmo
 * {@link MonthlySummaryInput} produz sempre o mesmo {@link MonthlySummaryFigures}. Quem lê os dados e grava o resultado é o
 * {@code MonthlySummaryService}.
 * <ul>
 *   <li>{@code baseSalary}: salário vigente no período (zero antes da primeira vigência)</li>
 *   <li>{@code totalOverdue}: soma das despesas {@code PENDING} com vencimento antes da data de referência (atraso derivado)</li>
 *   <li>{@code totalFixed}: soma das despesas do tipo {@code FIXED}, pagas ou não</li>
 *   <li>{@code cashBalance}: {@code remainingAmount} do mês anterior (carryover automático); zero se não houver</li>
 *   <li>{@code previousMonthMissing}: {@code true} quando não há resumo do mês anterior. O {@code cashBalance} zero vem dessa falta
 *       (o cálculo não inventa histórico) e o cliente fica sabendo; vale também para o primeiro mês do usuário</li>
 *   <li>{@code totalIncome}: {@code baseSalary + totalExtras + cashBalance}</li>
 *   <li>{@code remainingAmount}: {@code totalIncome − totalExpenses} (quanto sobra ao fim do mês, descontando pago + pendente)</li>
 *   <li>{@code savingsYield}: poupança atual − poupança do mês anterior; zero se não houver anterior</li>
 *   <li>{@code savingsBalance}: saldo da poupança do usuário no momento do cálculo (retrato)</li>
 *   <li>{@code categoryTotals}: quanto foi gasto em cada categoria (pago + pendente), ordenado pelo nome da categoria</li>
 * </ul>
 */
public final class MonthlySummaryCalculator {

    private MonthlySummaryCalculator() {
    }

    /**
     * Calcula os números do resumo a partir das despesas e dos ganhos do mês, do salário vigente, da poupança e do resumo anterior.
     *
     * @param input fatos do mês; a data de referência ("hoje") chega nele, o cálculo não lê o relógio
     * @return os números do resumo, incluindo o total por categoria
     */
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
