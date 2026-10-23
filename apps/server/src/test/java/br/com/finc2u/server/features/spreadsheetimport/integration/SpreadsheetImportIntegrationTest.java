package br.com.finc2u.server.features.spreadsheetimport.integration;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.card.service.CardAccountService;
import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import br.com.finc2u.server.features.extraincome.service.ExtraIncomeService;
import br.com.finc2u.server.features.recurringexpense.service.RecurringExpenseService;
import br.com.finc2u.server.features.spreadsheetimport.reader.SpreadsheetReader;
import br.com.finc2u.server.features.spreadsheetimport.service.SpreadsheetImportService;
import br.com.finc2u.server.features.spreadsheetimport.support.SpreadsheetWorkbookBuilder;
import br.com.finc2u.server.features.spreadsheetimport.vo.ImportOptions;
import br.com.finc2u.server.features.spreadsheetimport.vo.SpreadsheetData;
import br.com.finc2u.server.features.spreadsheetimport.vo.SpreadsheetMonthTotals;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SpreadsheetImportIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final String NUBANK = "NUBANK                                      Fechamento: 09                                              Vencimento: 15";
    private static final String PLACEHOLDER = "--------------";

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }

    }

    @TempDir
    Path dir;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CardAccountService cardAccountService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private RecurringExpenseService recurringExpenseService;

    @Autowired
    private ExtraIncomeService extraIncomeService;

    @Autowired
    private SpreadsheetReader reader;

    @Autowired
    private SpreadsheetImportService importService;

    // Os blocos de agosto, setembro e outubro de 2026 são pagos em setembro, outubro e novembro.
    private SpreadsheetData read(SpreadsheetWorkbookBuilder builder) {
        return reader.read(builder.build(dir.resolve("planilha.xlsx")), YearMonth.of(2026, 8));
    }

    @Test
    void importFor_shouldCreateTheCardWithTheSheetDays_andReuseOneThatAlreadyExistsWithAnotherCase() {
        var userId = registerUser("importa.cartao@email.com");
        cardAccountService.create(CardAccount.of("Mercado pago", 1, 2, null), userId);
        var builder = new SpreadsheetWorkbookBuilder();
        builder.month(2026, 8)
            .line(NUBANK, 10, null)
            .line("MERCADO PAGO Fechamento: 09 Vencimento: 14", 20, null);

        var result = importService.importFor(userId, read(builder), ImportOptions.defaults());

        assertEquals(1, result.createdCards());
        var cards = cardAccountService.getByUser(userId, null);
        assertEquals(List.of("Mercado pago", "Nubank"), cards.stream().map(CardAccount::getCardName).toList());
        var nubank = cards.stream().filter(card -> card.getCardName().equals("Nubank")).findFirst().orElseThrow();
        assertEquals(9, nubank.getClosingDate());
        assertEquals(15, nubank.getDueDate());
    }

    @Test
    void importFor_shouldGroupTheInstallmentsOfOnePurchase_andKeepTheValueAndPaymentOfEachMonth() {
        var userId = registerUser("importa.parcela@email.com");
        var builder = new SpreadsheetWorkbookBuilder();
        builder.month(2026, 8).line(NUBANK, 13.69, LocalDate.of(2026, 3, 4)).line("TOTAL DA FATURA", 13.69, "PG");
        builder.month(2026, 9).line(NUBANK, 13.64, LocalDate.of(2026, 4, 4)).line("TOTAL DA FATURA", 13.64, null);

        importService.importFor(userId, read(builder), ImportOptions.defaults());

        var rows = installmentsOf(userId);
        assertEquals(2, rows.size());
        assertEquals(rows.get(0).getInstallmentGroupId(), rows.get(1).getInstallmentGroupId());
        assertEquals(List.of(3, 4), rows.stream().map(Expense::getCurrentInstallment).toList());
        assertEquals(List.of(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 15)), rows.stream().map(Expense::getDueDate).toList());
        assertEquals(List.of(money("13.69"), money("13.64")), rows.stream().map(Expense::getValue).toList());
        assertEquals(PaymentStatus.PAID, rows.get(0).getPaymentStatus());
        assertNull(rows.get(0).getPaidAt());
        assertEquals(PaymentStatus.PENDING, rows.get(1).getPaymentStatus());
        assertEquals("Compra no Nubank", rows.get(0).getDescription());
        assertEquals(ExpenseType.PARCELED, rows.get(0).getExpenseType());
    }

    // A compra 1/4 de agosto tem a parcela 3/4 em outubro e a planilha não tem a 2/4 de setembro: a de setembro some.
    @Test
    void importFor_shouldDropTheInstallmentTheSheetDoesNotHaveInAMonthItCovers_andKeepTheProjection() {
        var userId = registerUser("importa.projecao@email.com");
        var builder = new SpreadsheetWorkbookBuilder();
        builder.month(2026, 8).line(NUBANK, 50, LocalDate.of(2026, 1, 4));
        builder.month(2026, 9).line(NUBANK, 7, null);
        builder.month(2026, 10).line(NUBANK, 50, LocalDate.of(2026, 3, 4));

        importService.importFor(userId, read(builder), ImportOptions.defaults());

        var months = installmentsOf(userId).stream().filter(row -> row.getCurrentInstallment() != null).map(row -> YearMonth.from(row.getDueDate())).toList();
        assertEquals(List.of(YearMonth.of(2026, 9), YearMonth.of(2026, 11), YearMonth.of(2026, 12)), months);
    }

    @Test
    void importFor_shouldTurnTheFixedItemsIntoRecurringModels_withTheirPastMonthsAndOneModelPerValue() {
        var userId = registerUser("importa.fixos@email.com");
        var builder = new SpreadsheetWorkbookBuilder();
        builder.month(2026, 8).line(NUBANK, 5, null).line("FIXOS", 20.9, "Netflix").line(null, 14.9, "iCloud").line("TOTAL DA FATURA", 40.8, "PG");
        builder.month(2026, 9).line(NUBANK, 5, null).line("FIXOS", 20.9, "Netflix").line(null, 19.9, "iCloud").line("TOTAL DA FATURA", 45.8, "PG");
        builder.month(2026, 10).line(NUBANK, 5, null).line("FIXOS", 20.9, "Netflix").line(null, 19.9, "iCloud").line("TOTAL DA FATURA", 45.8, null);

        importService.importFor(userId, read(builder), ImportOptions.defaults());

        var models = recurringExpenseService.getByUser(userId);
        assertEquals(3, models.size());
        var netflix = models.stream().filter(model -> model.getDescription().equals("Netflix")).findFirst().orElseThrow();
        assertNull(netflix.getEndMonth());
        assertEquals(YearMonth.of(2026, 9).atDay(1), netflix.getStartMonth());
        var oldICloud = models.stream().filter(model -> model.getValue().compareTo(money("14.9")) == 0).findFirst().orElseThrow();
        assertEquals(YearMonth.of(2026, 9).atDay(1), oldICloud.getEndMonth());

        var fixed = expensesOf(userId).stream().filter(expense -> expense.getRecurringExpenseId() != null).toList();
        assertEquals(6, fixed.size());
        var netflixRows = fixed.stream().filter(expense -> netflix.getId().equals(expense.getRecurringExpenseId())).sorted(Comparator.comparing(Expense::getDueDate)).toList();
        assertEquals(List.of(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 15)), netflixRows.stream().map(Expense::getDueDate).toList());
        assertEquals(List.of(PaymentStatus.PAID, PaymentStatus.PAID, PaymentStatus.PENDING), netflixRows.stream().map(Expense::getPaymentStatus).toList());
    }

    // Cartão que vence antes de fechar (fecha dia 30, vence dia 15): a despesa fixa do mês de pagamento cai no dia 15 dele.
    @Test
    void importFor_shouldKeepTheFixedItemOnTheDueDayOfThePaymentMonth_whenTheCardDueDayIsBeforeItsClosingDay() {
        var userId = registerUser("importa.fixo.renner@email.com");
        var builder = new SpreadsheetWorkbookBuilder();
        builder.month(2026, 8).line("RENNER                              Fechamento: 30 Vencimento: 15", 1, null).line("FIXOS", 10, "Seguro");
        builder.month(2026, 9).line("RENNER                              Fechamento: 30 Vencimento: 15", 1, null).line("FIXOS", 10, "Seguro");

        importService.importFor(userId, read(builder), ImportOptions.defaults());

        // O modelo não tem fim: o gerador ainda cria os próximos meses (novembro e dezembro), fora do que a planilha cobre.
        var rows = expensesOf(userId).stream().filter(expense -> expense.getRecurringExpenseId() != null
                && !expense.getDueDate().isAfter(LocalDate.of(2026, 10, 31)))
            .sorted(Comparator.comparing(Expense::getDueDate)).toList();
        assertEquals(List.of(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 15)), rows.stream().map(Expense::getDueDate).toList());
    }

    @Test
    void importFor_shouldTurnTheGeneralIntoOneExpensePerCardAndMonth_andTheNegativeIntoAnAdjustmentIncome() {
        var userId = registerUser("importa.gerais@email.com");
        var builder = new SpreadsheetWorkbookBuilder();
        builder.month(2026, 8).line(NUBANK, 5, null).line("GERAIS", 925.57, PLACEHOLDER).line("TOTAL DA FATURA", 930.57, "PG");
        builder.month(2026, 9).line(NUBANK, 5, null).line("GERAIS", -19.15, PLACEHOLDER).line("TOTAL DA FATURA", -14.15, null);

        importService.importFor(userId, read(builder), ImportOptions.defaults());

        var general = expensesOf(userId).stream().filter(expense -> expense.getDescription().startsWith("Compras à vista")).toList();
        assertEquals(1, general.size());
        assertEquals("Compras à vista no Nubank", general.getFirst().getDescription());
        assertEquals(money("925.57"), general.getFirst().getValue());
        assertEquals(ExpenseType.VARIABLE, general.getFirst().getExpenseType());
        assertEquals(PaymentStatus.PAID, general.getFirst().getPaymentStatus());

        var incomes = extraIncomeService.getByUser(userId);
        assertEquals(1, incomes.size());
        assertEquals("Ajuste de balanço no Nubank", incomes.getFirst().getDescription());
        assertEquals(money("19.15"), incomes.getFirst().getValue());
        assertEquals(LocalDate.of(2026, 10, 31), incomes.getFirst().getDate());
    }

    @Test
    void importFor_shouldUseTheLastDayOfTheMonthForBillsAndExtras_orTheInformedDay() {
        var userId = registerUser("importa.contas@email.com");
        var other = registerUser("importa.contas.dia@email.com");
        var builder = new SpreadsheetWorkbookBuilder();
        builder.month(2026, 8)
            .line("CONTAS FIXAS", 1014, "FACULDADE - PG")
            .line("TOTAL DE CONTAS", 1014, null)
            .line("VIAGEM CABO FRIO", 205, "PG");
        var data = read(builder);

        importService.importFor(userId, data, ImportOptions.defaults());
        importService.importFor(other, data, new ImportOptions(null, null, 10));

        var bill = expensesOf(userId).stream().filter(expense -> expense.getDescription().equals("FACULDADE")).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026, 9, 30), bill.getDueDate());
        assertEquals(ExpenseType.FIXED, bill.getExpenseType());
        assertEquals(PaymentStatus.PAID, bill.getPaymentStatus());
        assertNull(bill.getPaidAt());
        var extra = expensesOf(other).stream().filter(expense -> expense.getDescription().equals("VIAGEM CABO FRIO")).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026, 9, 10), extra.getDueDate());
        assertEquals(ExpenseType.VARIABLE, extra.getExpenseType());
    }

    @Test
    void importFor_shouldTurnEveryIncomeIntoAnExtraIncomeOfThePaymentMonth() {
        var userId = registerUser("importa.renda@email.com");
        var builder = new SpreadsheetWorkbookBuilder();
        builder.month(2026, 8)
            .line(NUBANK, 5, null)
            .line("SALÁRIO", null, null)
            .line("Seguro Desemprego", 1518, null)
            .line("Parte Mãe - PG", 2600, null);

        importService.importFor(userId, read(builder), ImportOptions.defaults());

        var incomes = extraIncomeService.getByUser(userId);
        assertEquals(2, incomes.size());
        assertTrue(incomes.stream().allMatch(income -> income.getDate().equals(LocalDate.of(2026, 9, 30))));
        assertEquals(List.of("Parte Mãe", "Seguro Desemprego"), incomes.stream().map(ExtraIncome::getDescription).sorted().toList());
    }

    @Test
    void importFor_shouldPickTheCategoryByTheUserRule_andFallBackToOthers() {
        var userId = registerUser("importa.categoria@email.com");
        var builder = new SpreadsheetWorkbookBuilder();
        builder.month(2026, 8)
            .line("CONTAS FIXAS", 1014, "FACULDADE - PG")
            .line(null, 99, "COMBÚSTIVEL")
            .line("TOTAL DE CONTAS", 1113, null)
            .line("PRESENTE ISABELA", 55, "PG");
        var options = new ImportOptions(Map.of("facul", "Educação", "combustivel", "Transporte"), null, null);

        importService.importFor(userId, read(builder), options);

        var categoryByDescription = expensesOf(userId).stream().collect(Collectors.toMap(Expense::getDescription, expense -> expense.getCategory().getName()));
        assertEquals("Educação", categoryByDescription.get("FACULDADE"));
        assertEquals("Transporte", categoryByDescription.get("COMBÚSTIVEL"));
        assertEquals("Outros", categoryByDescription.get("PRESENTE ISABELA"));
        assertTrue(categoryService.getByUser(userId).stream().anyMatch(category -> category.getName().equals("Educação")));
    }

    @Test
    void importFor_shouldNotDuplicateAnything_whenRunAgain() {
        var userId = registerUser("importa.repete@email.com");
        var data = read(fullSheet());

        var first = importService.importFor(userId, data, ImportOptions.defaults());
        var expensesAfterFirst = expensesOf(userId).size();
        var incomesAfterFirst = extraIncomeService.getByUser(userId).size();
        var modelsAfterFirst = recurringExpenseService.getByUser(userId).size();
        var second = importService.importFor(userId, data, ImportOptions.defaults());

        assertTrue(first.createdExpenses() > 0);
        assertEquals(0, second.createdExpenses());
        assertEquals(0, second.createdExtraIncomes());
        assertEquals(0, second.createdRecurringExpenses());
        assertEquals(0, second.createdCards());
        assertNotEquals(0, second.alreadyThere());
        assertEquals(expensesAfterFirst, expensesOf(userId).size());
        assertEquals(incomesAfterFirst, extraIncomeService.getByUser(userId).size());
        assertEquals(modelsAfterFirst, recurringExpenseService.getByUser(userId).size());
    }

    // O total de cada mês de pagamento no backend é o da planilha: o que a planilha calcula como TOTAL A PAGAR.
    @Test
    void importFor_shouldReproduceTheMonthlyTotalsOfTheSheet() {
        var userId = registerUser("importa.totais@email.com");
        var data = read(fullSheet());

        importService.importFor(userId, data, ImportOptions.defaults());

        var totalBySheet = data.totals().stream().collect(Collectors.toMap(SpreadsheetMonthTotals::paymentMonth, SpreadsheetMonthTotals::totalToPay));
        for (var entry : totalBySheet.entrySet()) {
            var month = entry.getKey();
            var total = expenseService.getByUserAndPeriod(userId, month.atDay(1), month.atEndOfMonth()).stream()
                .map(Expense::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, entry.getValue().compareTo(total), "mês " + month + ": planilha " + entry.getValue() + ", backend " + total);
        }
    }

    // Duas faturas, parcelas que variam em centavos, fixos que mudam de valor, conta, extra e renda, em três meses.
    private SpreadsheetWorkbookBuilder fullSheet() {
        var builder = new SpreadsheetWorkbookBuilder();
        builder.month(2026, 8)
            .line(NUBANK, 13.69, LocalDate.of(2026, 3, 10))
            .line(null, 40, LocalDate.of(2026, 1, 3))
            .line("FIXOS", 20.9, "Netflix")
            .line("GERAIS", 100, PLACEHOLDER)
            .line("TOTAL DA FATURA", 174.59, "PG")
            .line("CONTAS FIXAS", 1014, "FACULDADE - PG")
            .line("TOTAL DE CONTAS", 1014, null)
            .line("VIAGEM CABO FRIO", 205, "PG")
            .line("TOTAL DE EXTRAS", 205, null)
            .line("TOTAL A PAGAR ", 1393.59, null)
            .line("SALÁRIO", null, null)
            .line("Seguro Desemprego", 1518, null)
            .line("TOTAL A RECEBER", 1518, null);
        builder.month(2026, 9)
            .line(NUBANK, 13.64, LocalDate.of(2026, 4, 10))
            .line(null, 40, LocalDate.of(2026, 2, 3))
            .line("FIXOS", 20.9, "Netflix")
            .line("GERAIS", 80, PLACEHOLDER)
            .line("TOTAL DA FATURA", 154.54, "PG")
            .line("CONTAS FIXAS", 1014, "FACULDADE - PG")
            .line("TOTAL DE CONTAS", 1014, null)
            .line("TOTAL DE EXTRAS", 0, null)
            .line("TOTAL A PAGAR ", 1168.54, null);
        builder.month(2026, 10)
            .line(NUBANK, 13.69, LocalDate.of(2026, 5, 10))
            .line("FIXOS", 20.9, "Netflix")
            .line("GERAIS", 60, PLACEHOLDER)
            .line("TOTAL DA FATURA", 94.59, null)
            .line("CONTAS FIXAS", 1014, "FACULDADE")
            .line("TOTAL DE CONTAS", 1014, null)
            .line("ÔNIBUS", 80.32, "04/11 - PG")
            .line("TOTAL DE EXTRAS", 80.32, null)
            .line("TOTAL A PAGAR ", 1188.91, null);
        return builder;
    }

    private List<Expense> installmentsOf(UUID userId) {
        return expensesOf(userId).stream()
            .filter(expense -> expense.getExpenseType() == ExpenseType.PARCELED)
            .sorted(Comparator.comparing(Expense::getDueDate))
            .toList();
    }

    private List<Expense> expensesOf(UUID userId) {
        return expenseService.getByUser(userId);
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private UUID registerUser(String email) {
        var user = User.builder()
            .name("Ana")
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build();
        return userService.create(user, BigDecimal.valueOf(3000), LocalDate.of(2026, 1, 1)).getId();
    }

}
