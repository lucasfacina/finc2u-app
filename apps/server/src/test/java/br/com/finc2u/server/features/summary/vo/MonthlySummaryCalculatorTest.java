package br.com.finc2u.server.features.summary.vo;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MonthlySummaryCalculatorTest {

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value);
    }

    private static Expense expense(double value, PaymentStatus status) {
        return Expense.builder()
            .value(money(value))
            .paymentStatus(status)
            .expenseType(ExpenseType.VARIABLE)
            .build();
    }

    private static ExtraIncome extraIncome(double value) {
        return ExtraIncome.builder()
            .value(money(value))
            .build();
    }

    private static MonthlySummaryInput input(List<Expense> expenses, List<ExtraIncome> extraIncomes, double baseSalary,
                                             double savingsBalance, BigDecimal previousRemaining, BigDecimal previousSavings) {
        return new MonthlySummaryInput(expenses, extraIncomes, money(baseSalary), money(savingsBalance), previousRemaining,
            previousSavings);
    }

    private static void assertMoney(double expected, BigDecimal actual) {
        assertEquals(0, money(expected).compareTo(actual), "esperado " + expected + " mas foi " + actual);
    }

    // ---- cashBalance automático (carryover do mês anterior) ----

    @Test
    void calculate_shouldSetCashBalance_fromPreviousMonthRemaining() {
        var figures = MonthlySummaryCalculator.calculate(
            input(List.of(), List.of(), 3000, 0, money(350), null));

        assertMoney(350, figures.cashBalance());
    }

    @Test
    void calculate_shouldSetCashBalanceToZero_whenThereIsNoPreviousMonth() {
        var figures = MonthlySummaryCalculator.calculate(
            input(List.of(), List.of(), 3000, 0, null, null));

        assertMoney(0, figures.cashBalance());
    }

    // O mês anterior fechou no vermelho: o saldo negativo é levado adiante, não zerado.
    @Test
    void calculate_shouldCarryTheNegativeRemainingOverToTheNextMonth() {
        var figures = MonthlySummaryCalculator.calculate(
            input(List.of(), List.of(), 3000, 0, money(-100), null));

        assertMoney(-100, figures.cashBalance());
        assertMoney(2900, figures.totalIncome());
    }

    // ---- fórmulas ----

    @Test
    void calculate_shouldComputeIncomeAndRemainingAmount() {
        // totalIncome = baseSalary(3000) + totalExtras(500) + cashBalance(200) = 3700
        // remainingAmount = totalIncome(3700) - totalExpenses(1000) = 2700
        var figures = MonthlySummaryCalculator.calculate(input(
            List.of(expense(600, PaymentStatus.PAID), expense(400, PaymentStatus.PENDING)),
            List.of(extraIncome(500)), 3000, 0, money(200), null));

        assertMoney(3000, figures.baseSalary());
        assertMoney(3700, figures.totalIncome());
        assertMoney(2700, figures.remainingAmount());
    }

    @Test
    void calculate_shouldGoNegative_whenTheExpensesExceedTheIncome() {
        var figures = MonthlySummaryCalculator.calculate(input(
            List.of(expense(1500, PaymentStatus.PENDING)), List.of(), 1000, 0, null, null));

        assertMoney(-500, figures.remainingAmount());
    }

    @Test
    void calculate_shouldAggregateExpensesByStatusAndSumTheExtraIncomes() {
        var figures = MonthlySummaryCalculator.calculate(input(
            List.of(expense(600, PaymentStatus.PAID), expense(400, PaymentStatus.PENDING), expense(50, PaymentStatus.PAID)),
            List.of(extraIncome(300), extraIncome(200)), 3000, 0, null, null));

        assertMoney(1050, figures.totalExpenses());
        assertMoney(650, figures.totalPaid());
        assertMoney(400, figures.totalPending());
        assertMoney(500, figures.totalExtras());
    }

    @Test
    void calculate_shouldReturnTheSalaryAsTheRemaining_whenTheMonthHasNoMovement() {
        var figures = MonthlySummaryCalculator.calculate(
            input(List.of(), List.of(), 3000, 0, null, null));

        assertMoney(0, figures.totalExpenses());
        assertMoney(0, figures.totalExtras());
        assertMoney(3000, figures.remainingAmount());
    }

    // ---- baseSalary (snapshot do período) ----

    @Test
    void calculate_shouldUseTheBaseSalaryOfThePeriod_asTheIncome() {
        var figures = MonthlySummaryCalculator.calculate(
            input(List.of(), List.of(), 3500, 0, null, null));

        assertMoney(3500, figures.baseSalary());
        assertMoney(3500, figures.totalIncome());
    }

    @Test
    void calculate_shouldAcceptAZeroSalary_beforeTheFirstVigencia() {
        var figures = MonthlySummaryCalculator.calculate(input(
            List.of(expense(100, PaymentStatus.PENDING)), List.of(), 0, 0, null, null));

        assertMoney(0, figures.totalIncome());
        assertMoney(-100, figures.remainingAmount());
    }

    // ---- poupança ----

    @Test
    void calculate_shouldKeepTheSavingsBalanceAsASnapshot() {
        var figures = MonthlySummaryCalculator.calculate(
            input(List.of(), List.of(), 3000, 1500, null, null));

        assertMoney(1500, figures.savingsBalance());
    }

    @Test
    void calculate_shouldComputeTheSavingsYield_fromThePreviousMonthSavingsBalance() {
        // savingsYield = savingsBalance(1500) - previousSavingsBalance(1200) = 300
        var figures = MonthlySummaryCalculator.calculate(
            input(List.of(), List.of(), 3000, 1500, null, money(1200)));

        assertMoney(300, figures.savingsYield());
    }

    @Test
    void calculate_shouldSetTheSavingsYieldToZero_whenThereIsNoPreviousMonth() {
        var figures = MonthlySummaryCalculator.calculate(
            input(List.of(), List.of(), 3000, 1500, null, null));

        assertMoney(0, figures.savingsYield());
    }

    // O critério de determinismo do TCC: a mesma entrada produz sempre a mesma saída.
    @Test
    void calculate_shouldBeDeterministic_forTheSameInput() {
        var input = input(
            List.of(expense(600, PaymentStatus.PAID), expense(400, PaymentStatus.PENDING)),
            List.of(extraIncome(500)), 3000, 1500, money(200), money(1200));

        assertEquals(MonthlySummaryCalculator.calculate(input), MonthlySummaryCalculator.calculate(input));
    }

}
