package br.com.finc2u.server.features.summary.vo;

import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MonthlySummaryCalculatorTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 6, 15);
    private static final Category GROCERIES = categoryOf("Alimentação");
    private static final Category LEISURE = categoryOf("Lazer");

    private static Category categoryOf(String name) {
        return Category.builder()
            .name(name)
            .build();
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value);
    }

    // Sem vencimento informado, a despesa vence depois da data de referência (não conta como atrasada).
    private static Expense expense(double value, PaymentStatus status) {
        return expense(value, status, ExpenseType.VARIABLE, REFERENCE_DATE.plusDays(10));
    }

    private static Expense expense(double value, PaymentStatus status, ExpenseType type, LocalDate dueDate) {
        return expense(value, status, type, dueDate, GROCERIES);
    }

    private static Expense expense(double value, PaymentStatus status, ExpenseType type, LocalDate dueDate, Category category) {
        return Expense.builder()
            .value(money(value))
            .paymentStatus(status)
            .expenseType(type)
            .dueDate(dueDate)
            .category(category)
            .build();
    }

    private static ExtraIncome extraIncome(double value) {
        return ExtraIncome.builder()
            .value(money(value))
            .build();
    }

    private static MonthlySummaryInput input(List<Expense> expenses, List<ExtraIncome> extraIncomes, double baseSalary,
                                             double savingsBalance, BigDecimal previousRemaining, BigDecimal previousSavings) {
        return new MonthlySummaryInput(expenses, extraIncomes, REFERENCE_DATE, money(baseSalary), money(savingsBalance),
            previousRemaining, previousSavings);
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

    // Sem resumo do mês anterior o cashBalance é zero e isso fica explícito: o cliente sabe que o zero vem da falta.
    @Test
    void calculate_shouldFlagThePreviousMonthAsMissing_whenThereIsNoPreviousSummary() {
        var figures = MonthlySummaryCalculator.calculate(
            input(List.of(), List.of(), 3000, 0, null, null));

        assertTrue(figures.previousMonthMissing());
        assertMoney(0, figures.cashBalance());
    }

    @Test
    void calculate_shouldNotFlagThePreviousMonthAsMissing_whenThereIsAPreviousSummary() {
        var figures = MonthlySummaryCalculator.calculate(
            input(List.of(), List.of(), 3000, 0, money(350), null));

        assertFalse(figures.previousMonthMissing());
    }

    // Um mês anterior que fechou em zero existe: o zero do cashBalance, aqui, não é falta de resumo.
    @Test
    void calculate_shouldNotFlagThePreviousMonthAsMissing_whenItClosedAtZero() {
        var figures = MonthlySummaryCalculator.calculate(
            input(List.of(), List.of(), 3000, 0, BigDecimal.ZERO, null));

        assertFalse(figures.previousMonthMissing());
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

    // ---- totais por categoria (base do orçamento) ----

    @Test
    void calculate_shouldSumTheExpensesOfEachCategory_paidOrPending_sortedByName() {
        var figures = MonthlySummaryCalculator.calculate(input(List.of(
            expense(70, PaymentStatus.PENDING, ExpenseType.VARIABLE, REFERENCE_DATE.plusDays(3), LEISURE),
            expense(300, PaymentStatus.PAID, ExpenseType.VARIABLE, REFERENCE_DATE.plusDays(1), GROCERIES),
            expense(50, PaymentStatus.PENDING, ExpenseType.FIXED, REFERENCE_DATE.plusDays(2), GROCERIES)
        ), List.of(), 3000, 0, null, null));

        assertEquals(List.of("Alimentação", "Lazer"),
            figures.categoryTotals().stream().map(total -> total.category().getName()).toList());
        assertMoney(350, figures.categoryTotals().get(0).total());
        assertMoney(70, figures.categoryTotals().get(1).total());
    }

    @Test
    void calculate_shouldSortTheCategoryTotalsInPortugueseOrder_whateverTheOrderOfTheExpenses() {
        var expenses = Stream.of("Zebra", "ônibus", "Alimentação", "Água", "Ótica", "alimentação")
            .map(name -> expense(10, PaymentStatus.PENDING, ExpenseType.VARIABLE, REFERENCE_DATE.plusDays(1), Category.of(name)))
            .toList();

        var figures = MonthlySummaryCalculator.calculate(input(expenses, List.of(), 3000, 0, null, null));

        assertEquals(List.of("Água", "alimentação", "Alimentação", "ônibus", "Ótica", "Zebra"),
            figures.categoryTotals().stream().map(total -> total.category().getName()).toList());
    }

    @Test
    void calculate_shouldMatchTheSumOfTheCategoryTotalsWithTheTotalExpenses() {
        var figures = MonthlySummaryCalculator.calculate(input(List.of(
            expense(70, PaymentStatus.PENDING, ExpenseType.VARIABLE, REFERENCE_DATE.plusDays(3), LEISURE),
            expense(300, PaymentStatus.PAID, ExpenseType.VARIABLE, REFERENCE_DATE.plusDays(1), GROCERIES)
        ), List.of(), 3000, 0, null, null));

        var sum = figures.categoryTotals().stream().map(CategoryTotal::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, figures.totalExpenses().compareTo(sum));
    }

    @Test
    void calculate_shouldReturnNoCategoryTotal_whenTheMonthHasNoExpense() {
        var figures = MonthlySummaryCalculator.calculate(
            input(List.of(), List.of(extraIncome(500)), 3000, 0, null, null));

        assertEquals(List.of(), figures.categoryTotals());
    }

    // ---- totalOverdue: atraso derivado da data de referência ----

    @Test
    void calculate_shouldSumOnlyThePendingExpensesDueBeforeTheReferenceDate_asOverdue() {
        var figures = MonthlySummaryCalculator.calculate(input(List.of(
            expense(100, PaymentStatus.PENDING, ExpenseType.VARIABLE, REFERENCE_DATE.minusDays(1)),
            expense(40, PaymentStatus.PENDING, ExpenseType.FIXED, REFERENCE_DATE.minusDays(30)),
            expense(500, PaymentStatus.PAID, ExpenseType.VARIABLE, REFERENCE_DATE.minusDays(10)),
            expense(70, PaymentStatus.PENDING, ExpenseType.VARIABLE, REFERENCE_DATE.plusDays(1))
        ), List.of(), 3000, 0, null, null));

        assertMoney(140, figures.totalOverdue());
    }

    // Vence hoje não é atraso: o atraso é estritamente anterior à data de referência.
    @Test
    void calculate_shouldNotCountAnExpenseDueOnTheReferenceDate_asOverdue() {
        var figures = MonthlySummaryCalculator.calculate(input(List.of(
            expense(100, PaymentStatus.PENDING, ExpenseType.VARIABLE, REFERENCE_DATE)
        ), List.of(), 3000, 0, null, null));

        assertMoney(0, figures.totalOverdue());
    }

    @Test
    void calculate_shouldReturnZeroOverdue_whenNothingIsLate() {
        var figures = MonthlySummaryCalculator.calculate(input(
            List.of(expense(100, PaymentStatus.PENDING), expense(50, PaymentStatus.PAID)), List.of(), 3000, 0, null, null));

        assertMoney(0, figures.totalOverdue());
    }

    @Test
    void calculate_shouldDeriveTheOverdueFromTheReferenceDate_notFromTheClock() {
        var expenses = List.of(expense(100, PaymentStatus.PENDING, ExpenseType.VARIABLE, LocalDate.of(2026, 6, 10)));

        var beforeDue = MonthlySummaryCalculator.calculate(new MonthlySummaryInput(expenses, List.of(),
            LocalDate.of(2026, 6, 10), money(3000), money(0), null, null));
        var afterDue = MonthlySummaryCalculator.calculate(new MonthlySummaryInput(expenses, List.of(),
            LocalDate.of(2026, 6, 11), money(3000), money(0), null, null));

        assertMoney(0, beforeDue.totalOverdue());
        assertMoney(100, afterDue.totalOverdue());
    }

    // ---- totalFixed ----

    @Test
    void calculate_shouldSumTheFixedExpenses_paidOrNot_andLeaveTheOthersOut() {
        var figures = MonthlySummaryCalculator.calculate(input(List.of(
            expense(20.90, PaymentStatus.PAID, ExpenseType.FIXED, REFERENCE_DATE.plusDays(5)),
            expense(39.00, PaymentStatus.PENDING, ExpenseType.FIXED, REFERENCE_DATE.plusDays(5)),
            expense(350, PaymentStatus.PAID, ExpenseType.VARIABLE, REFERENCE_DATE.plusDays(5)),
            expense(100, PaymentStatus.PENDING, ExpenseType.PARCELED, REFERENCE_DATE.plusDays(5))
        ), List.of(), 3000, 0, null, null));

        assertMoney(59.90, figures.totalFixed());
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
