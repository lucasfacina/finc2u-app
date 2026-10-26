package br.com.finc2u.server.features.spreadsheetimport.integration;

import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import br.com.finc2u.server.features.extraincome.service.ExtraIncomeService;
import br.com.finc2u.server.features.spreadsheetimport.reader.SpreadsheetReader;
import br.com.finc2u.server.features.spreadsheetimport.service.SpreadsheetImportService;
import br.com.finc2u.server.features.spreadsheetimport.vo.ImportOptions;
import br.com.finc2u.server.features.spreadsheetimport.vo.SpreadsheetData;
import br.com.finc2u.server.features.spreadsheetimport.vo.SpreadsheetMonthTotals;
import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.form.CategoryTotalResponse;
import br.com.finc2u.server.features.summary.form.MonthlySummaryResponse;
import br.com.finc2u.server.features.summary.service.MonthlySummaryService;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Confere o resumo mensal contra a planilha de gastos de verdade, só de leitura: o arquivo é aberto sem ser alterado, os dados vão para o
 * banco em memória dos testes (nunca o `data/` do projeto) e nada do arquivo vai para o repositório. Só roda quando a variável de
 * ambiente FINC2U_PLANILHA aponta para um `.xlsx` (sem ela a classe é ignorada, e o CI, que não tem o arquivo, segue verde). Serve tanto
 * para a planilha real quanto para a cópia anonimizada.
 *
 * Cada mês de pagamento do backend tem de fechar com o que a própria planilha calcula: `totalExpenses` com o TOTAL A PAGAR e
 * `totalIncome - cashBalance` com o TOTAL A RECEBER (o `remainingAmount` não entra: ele carrega o saldo do mês anterior, o SALDO DO MÊS da
 * planilha não). Uma exceção declarada: um crédito negativo do `GERAIS` a planilha abate do TOTAL A PAGAR, e o importador o grava como
 * renda "Ajuste de balanço" (a despesa não aceita valor negativo); nesses meses despesa e renda do backend passam do total da planilha
 * exatamente pelo valor do ajuste, que então é descontado dos dois lados. Com dezenas de meses e centenas de lançamentos, prova também que o histórico não faz N+1, que a cascata dá o mesmo
 * resultado calculando os meses fora de ordem e que o cálculo é determinístico.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "FINC2U_PLANILHA", matches = ".+")
class SpreadsheetMonthlySummaryValidationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);
    private static final YearMonth FIRST_BLOCK = YearMonth.of(2025, 4);
    private static final String ADJUSTMENT = "Ajuste de balanço";

    // Os números de um resumo, na escala do banco, para comparar dois cálculos sem depender da escala do BigDecimal.
    private record Figures(
        BigDecimal totalExpenses, BigDecimal totalPaid, BigDecimal totalPending, BigDecimal totalOverdue,
        BigDecimal totalFixed,
        BigDecimal totalExtras, BigDecimal cashBalance, BigDecimal totalIncome, BigDecimal remainingAmount,
        Boolean previousMonthMissing, Map<String, BigDecimal> categoryTotals
    ) {
        static Figures of(MonthlySummaryResponse summary) {
            var categories = summary.categoryTotals().stream()
                .collect(Collectors.toMap(CategoryTotalResponse::categoryName, total -> scaled(total.total())));

            return new Figures(
                scaled(summary.totalExpenses()), scaled(summary.totalPaid()), scaled(summary.totalPending()), scaled(summary.totalOverdue()),
                scaled(summary.totalFixed()), scaled(summary.totalExtras()), scaled(summary.cashBalance()), scaled(summary.totalIncome()),
                scaled(summary.remainingAmount()), summary.previousMonthMissing(), categories);
        }

        private static BigDecimal scaled(BigDecimal value) {
            return value.setScale(2, RoundingMode.HALF_UP);
        }
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }

    }

    @Autowired
    private UserService userService;

    @Autowired
    private SpreadsheetReader reader;

    @Autowired
    private SpreadsheetImportService importService;

    @Autowired
    private MonthlySummaryService monthlySummaryService;

    @Autowired
    private ExtraIncomeService extraIncomeService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private SpreadsheetData data;
    private List<SpreadsheetMonthTotals> sheetMonths;
    private UUID userId;
    private Map<YearMonth, Figures> inOrder;
    private Map<YearMonth, BigDecimal> adjustments;

    @BeforeAll
    void importAndCalculateInOrder() {
        data = reader.read(Path.of(System.getenv("FINC2U_PLANILHA")), FIRST_BLOCK);
        sheetMonths = data.totals().stream()
            .sorted(Comparator.comparing(SpreadsheetMonthTotals::paymentMonth))
            .toList();
        userId = registerAndImport("validacao.ordem@email.com");

        inOrder = calculate(userId, sheetMonths.stream().map(SpreadsheetMonthTotals::paymentMonth).toList());
        adjustments = extraIncomeService.getByUser(userId).stream()
            .filter(income -> income.getDescription().startsWith(ADJUSTMENT))
            .collect(Collectors.groupingBy(income -> YearMonth.from(income.getDate()),
                Collectors.reducing(BigDecimal.ZERO, ExtraIncome::getValue, BigDecimal::add)));
    }

    @Test
    void sheet_shouldHaveTheMonthsOfTheValidation_withoutGaps() {
        var months = sheetMonths.stream().map(SpreadsheetMonthTotals::paymentMonth).toList();

        assertFalse(months.isEmpty(), "a planilha não trouxe nenhum mês");
        for (var index = 1; index < months.size(); index++) {
            assertEquals(months.get(index - 1).plusMonths(1), months.get(index), "buraco entre os meses de pagamento");
        }
    }

    @Test
    void everyMonth_shouldReproduceTheTotalToPayOfTheSheet() {
        var mismatches = new ArrayList<String>();

        for (var month : sheetMonths) {
            var backend = inOrder.get(month.paymentMonth()).totalExpenses().subtract(adjustmentOf(month.paymentMonth()));
            if (month.totalToPay().compareTo(backend) != 0)
                mismatches.add(month.paymentMonth() + ": planilha " + month.totalToPay() + ", backend " + backend);
        }

        assertEquals(List.of(), mismatches, "meses com TOTAL A PAGAR diferente");
    }

    @Test
    void everyMonth_shouldReproduceTheTotalToReceiveOfTheSheet() {
        var mismatches = new ArrayList<String>();

        for (var month : sheetMonths) {
            var figures = inOrder.get(month.paymentMonth());
            var backend = figures.totalIncome().subtract(figures.cashBalance()).subtract(adjustmentOf(month.paymentMonth()));
            if (month.totalToReceive().compareTo(backend) != 0)
                mismatches.add(month.paymentMonth() + ": planilha " + month.totalToReceive() + ", backend " + backend);
        }

        assertEquals(List.of(), mismatches, "meses com TOTAL A RECEBER diferente");
    }

    @Test
    void everyMonth_shouldKeepTheCarryoverAndTheCategoryTotalsCoherent() {
        Figures previous = null;

        for (var month : sheetMonths) {
            var figures = inOrder.get(month.paymentMonth());
            var categorySum = figures.categoryTotals().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, figures.totalExpenses().compareTo(categorySum), "totais por categoria de " + month.paymentMonth());
            assertEquals(0, figures.totalIncome().subtract(figures.totalExpenses()).compareTo(figures.remainingAmount()), "saldo de " + month.paymentMonth());
            if (previous == null) {
                assertEquals(Boolean.TRUE, figures.previousMonthMissing(), "só o primeiro mês não tem mês anterior");
                assertEquals(0, BigDecimal.ZERO.compareTo(figures.cashBalance()));
            } else {
                assertEquals(Boolean.FALSE, figures.previousMonthMissing(), "mês anterior ausente em " + month.paymentMonth());
                assertEquals(0, previous.remainingAmount().compareTo(figures.cashBalance()), "carryover de " + month.paymentMonth());
            }
            previous = figures;
        }
    }

    @Test
    void history_shouldBeServedWithoutNPlusOne_whateverTheNumberOfMonths() {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var responses = monthlySummaryService.getByUser(userId).stream()
            .map(MonthlySummaryResponse::from)
            .toList();

        assertEquals(sheetMonths.size(), responses.size());
        assertTrue(responses.stream().noneMatch(response -> response.categoryTotals().isEmpty()));
        // Uma consulta para o usuário, outra para os resumos e outra para os totais por categoria de todos eles (a categoria vem junto), seja qual for o número de meses.
        assertEquals(3, statistics.getPrepareStatementCount(), "consultas do histórico de " + responses.size() + " meses");
    }

    @Test
    void cascade_shouldGiveTheSameResult_whenTheMonthsAreCalculatedOutOfOrder() {
        var otherUserId = registerAndImport("validacao.fora.de.ordem@email.com");
        var reversed = sheetMonths.stream().map(SpreadsheetMonthTotals::paymentMonth).sorted(Comparator.reverseOrder()).toList();

        calculate(otherUserId, reversed);
        var outOfOrder = readAll(otherUserId);

        assertEquals(inOrder, outOfOrder);
    }

    @Test
    void calculation_shouldBeDeterministic_whenRunTwice() {
        var months = sheetMonths.stream().map(SpreadsheetMonthTotals::paymentMonth).toList();

        var again = calculate(userId, months);

        assertEquals(inOrder, again);
    }

    private BigDecimal adjustmentOf(YearMonth month) {
        return adjustments.getOrDefault(month, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private UUID registerAndImport(String email) {
        var user = User.builder().name("Validação").email(email).savingsBalance(BigDecimal.ZERO).build();
        var id = userService.create(user, BigDecimal.ZERO, LocalDate.of(2025, 1, 1)).getId();
        importService.importFor(id, data, ImportOptions.defaults());

        return id;
    }

    private Map<YearMonth, Figures> calculate(UUID id, List<YearMonth> months) {
        for (var month : months) {
            monthlySummaryService.recalculateFrom(id, month);
        }

        return readAll(id);
    }

    private Map<YearMonth, Figures> readAll(UUID id) {
        return monthlySummaryService.getByUser(id).stream()
            .collect(Collectors.toMap(
                detail -> period(detail.summary()),
                detail -> Figures.of(MonthlySummaryResponse.from(detail))));
    }

    private YearMonth period(MonthlySummary summary) {
        return YearMonth.of(summary.getYear(), summary.getMonth());
    }

}
