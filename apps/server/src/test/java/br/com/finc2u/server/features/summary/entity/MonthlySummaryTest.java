package br.com.finc2u.server.features.summary.entity;

import br.com.finc2u.server.features.summary.vo.MonthlySummaryTotals;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.entity.UserConfiguration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MonthlySummaryTest {

    private static User userWith(BigDecimal baseSalary, BigDecimal savings) {
        return User.builder()
                .userConfiguration(UserConfiguration.builder()
                        .baseSalary(baseSalary)
                        .savingsBalance(savings)
                        .build())
                .build();
    }

    private static MonthlySummaryTotals totals(double expenses, double paid, double pending, double extras, double fixed) {
        return new MonthlySummaryTotals(
                BigDecimal.valueOf(expenses),
                BigDecimal.valueOf(paid),
                BigDecimal.valueOf(pending),
                BigDecimal.valueOf(extras),
                BigDecimal.valueOf(fixed)
        );
    }

    // ---- userIdForPeriod ----

    @Test
    void userIdForPeriod_shouldBuildSummaryWithCorrectId() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .build();
        user.setId(userId);

        MonthlySummary summary = MonthlySummary.userIdForPeriod(user, 6, 2026);

        assertEquals(userId, summary.getId().getUserId());
        assertEquals(6, summary.getId().getMonth());
        assertEquals(2026, summary.getId().getYear());
        assertSame(user, summary.getUser());
    }

    // ---- resolveTotals: prioridade do cashBalance ----

    @Test
    void resolveTotals_shouldUseRequestedCashBalance_whenProvided() {
        User user = userWith(BigDecimal.valueOf(3000), BigDecimal.ZERO);
        MonthlySummary summary = MonthlySummary.userIdForPeriod(user, 6, 2026);

        summary.resolveTotals(
                totals(1000, 600, 400, 0, 0),
                BigDecimal.valueOf(500),
                null
        );

        assertEquals(0, BigDecimal.valueOf(500).compareTo(summary.getCashBalance()));
    }

    @Test
    void resolveTotals_shouldPreserveExistingCashBalance_whenNoRequestAndAlreadySet() {
        User user = userWith(BigDecimal.valueOf(3000), BigDecimal.ZERO);
        MonthlySummary summary = MonthlySummary.userIdForPeriod(user, 6, 2026);
        summary.setCashBalance(BigDecimal.valueOf(200));

        summary.resolveTotals(
                totals(1000, 600, 400, 0, 0),
                null,
                null
        );

        assertEquals(0, BigDecimal.valueOf(200).compareTo(summary.getCashBalance()));
    }

    @Test
    void resolveTotals_shouldUsePreviousMonthRemaining_whenNoCashBalanceSet() {
        User user = userWith(BigDecimal.valueOf(3000), BigDecimal.ZERO);
        MonthlySummary summary = MonthlySummary.userIdForPeriod(user, 6, 2026);

        summary.resolveTotals(
                totals(1000, 600, 400, 0, 0),
                null,
                BigDecimal.valueOf(350)
        );

        assertEquals(0, BigDecimal.valueOf(350).compareTo(summary.getCashBalance()));
    }

    @Test
    void resolveTotals_shouldUseZero_whenNoCashBalanceAndNoPreviousMonth() {
        User user = userWith(BigDecimal.valueOf(3000), BigDecimal.ZERO);
        MonthlySummary summary = MonthlySummary.userIdForPeriod(user, 6, 2026);

        summary.resolveTotals(
                totals(1000, 600, 400, 0, 0),
                null,
                null
        );

        assertEquals(0, BigDecimal.ZERO.compareTo(summary.getCashBalance()));
    }

    @Test
    void resolveTotals_requestedCashBalance_shouldTakePrecedenceOverExistingAndPrevious() {
        User user = userWith(BigDecimal.valueOf(3000), BigDecimal.ZERO);
        MonthlySummary summary = MonthlySummary.userIdForPeriod(user, 6, 2026);
        summary.setCashBalance(BigDecimal.valueOf(200));

        summary.resolveTotals(
                totals(1000, 600, 400, 0, 0),
                BigDecimal.valueOf(999),
                BigDecimal.valueOf(111)
        );

        assertEquals(0, BigDecimal.valueOf(999).compareTo(summary.getCashBalance()));
    }

    // ---- resolveTotals: fórmulas ----

    @Test
    void resolveTotals_shouldComputeRemainingAmount() {
        // totalIncome = baseSalary(3000) + totalExtras(500) + cashBalance(200) = 3700
        // remainingAmount = totalIncome(3700) - totalExpenses(1000) = 2700
        User user = userWith(BigDecimal.valueOf(3000), BigDecimal.ZERO);
        MonthlySummary summary = MonthlySummary.userIdForPeriod(user, 6, 2026);

        summary.resolveTotals(
                totals(1000, 600, 400, 500, 0),
                BigDecimal.valueOf(200),
                null
        );

        assertEquals(0, BigDecimal.valueOf(2700).compareTo(summary.getRemainingAmount()));
    }

    @Test
    void resolveTotals_shouldComputeFlexibleBudget() {
        // totalIncome = 3000 + 500 + 200 = 3700
        // flexibleBudget = totalIncome(3700) - totalFixed(800) = 2900
        User user = userWith(BigDecimal.valueOf(3000), BigDecimal.ZERO);
        MonthlySummary summary = MonthlySummary.userIdForPeriod(user, 6, 2026);

        summary.resolveTotals(
                totals(1000, 600, 400, 500, 800),
                BigDecimal.valueOf(200),
                null
        );

        assertEquals(0, BigDecimal.valueOf(2900).compareTo(summary.getFlexibleBudget()));
    }

    @Test
    void resolveTotals_shouldPopulateAllTotalsFields() {
        User user = userWith(BigDecimal.valueOf(3000), BigDecimal.ZERO);
        MonthlySummary summary = MonthlySummary.userIdForPeriod(user, 6, 2026);

        summary.resolveTotals(
                totals(1000, 600, 400, 500, 200),
                BigDecimal.ZERO,
                null
        );

        assertEquals(0, BigDecimal.valueOf(1000).compareTo(summary.getTotalExpenses()));
        assertEquals(0, BigDecimal.valueOf(600).compareTo(summary.getTotalPaid()));
        assertEquals(0, BigDecimal.valueOf(400).compareTo(summary.getTotalPending()));
        assertEquals(0, BigDecimal.valueOf(500).compareTo(summary.getTotalExtras()));
    }

    // ---- resolveTotals: savingsBalance (snapshot) ----

    @Test
    void resolveTotals_shouldSnapshotSavingsBalance_fromUserConfiguration() {
        User user = userWith(BigDecimal.valueOf(3000), BigDecimal.valueOf(1500));
        MonthlySummary summary = MonthlySummary.userIdForPeriod(user, 6, 2026);

        summary.resolveTotals(
                totals(0, 0, 0, 0, 0),
                BigDecimal.ZERO,
                null
        );

        assertEquals(0, BigDecimal.valueOf(1500).compareTo(summary.getSavingsBalance()));
    }

    @Test
    void resolveTotals_shouldUseZeroSavings_whenUserHasNoConfiguration() {
        User user = userWith(null, null);
        MonthlySummary summary = MonthlySummary.userIdForPeriod(user, 6, 2026);

        summary.resolveTotals(
                totals(0, 0, 0, 0, 0),
                BigDecimal.ZERO,
                null
        );

        assertEquals(0, BigDecimal.ZERO.compareTo(summary.getSavingsBalance()));
    }

}
