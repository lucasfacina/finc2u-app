package br.com.finc2u.server.features.summary.entity;

import br.com.finc2u.server.features.summary.vo.MonthlySummaryFigures;
import br.com.finc2u.server.features.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MonthlySummaryTest {

    // ---- forPeriod ----

    @Test
    void forPeriod_shouldBuildSummaryWithPeriodAndUser() {
        var userId = UUID.randomUUID();
        var user = User.builder().build();
        user.setId(userId);

        var summary = MonthlySummary.forPeriod(user, 6, 2026);

        assertEquals(6, summary.getMonth());
        assertEquals(2026, summary.getYear());
        assertSame(user, summary.getUser());
    }

    // ---- apply: a entidade só grava os números que o MonthlySummaryCalculator calculou ----

    @Test
    void apply_shouldStoreEveryFigureWithoutChangingThePeriodOrTheUser() {
        var user = User.builder().build();
        var summary = MonthlySummary.forPeriod(user, 6, 2026);

        summary.apply(new MonthlySummaryFigures(
            BigDecimal.valueOf(3000),
            BigDecimal.valueOf(3700),
            BigDecimal.valueOf(1000),
            BigDecimal.valueOf(600),
            BigDecimal.valueOf(400),
            BigDecimal.valueOf(150),
            BigDecimal.valueOf(120),
            BigDecimal.valueOf(500),
            BigDecimal.valueOf(200),
            true,
            BigDecimal.valueOf(1500),
            BigDecimal.valueOf(300),
            BigDecimal.valueOf(2700),
            LocalDate.of(2026, 6, 15),
            List.of()
        ));

        assertEquals(0, BigDecimal.valueOf(3000).compareTo(summary.getBaseSalary()));
        assertEquals(0, BigDecimal.valueOf(3700).compareTo(summary.getTotalIncome()));
        assertEquals(0, BigDecimal.valueOf(1000).compareTo(summary.getTotalExpenses()));
        assertEquals(0, BigDecimal.valueOf(600).compareTo(summary.getTotalPaid()));
        assertEquals(0, BigDecimal.valueOf(400).compareTo(summary.getTotalPending()));
        assertEquals(0, BigDecimal.valueOf(150).compareTo(summary.getTotalOverdue()));
        assertEquals(0, BigDecimal.valueOf(120).compareTo(summary.getTotalFixed()));
        assertEquals(0, BigDecimal.valueOf(500).compareTo(summary.getTotalExtras()));
        assertEquals(0, BigDecimal.valueOf(200).compareTo(summary.getCashBalance()));
        assertTrue(summary.getPreviousMonthMissing());
        assertEquals(0, BigDecimal.valueOf(1500).compareTo(summary.getSavingsBalance()));
        assertEquals(0, BigDecimal.valueOf(300).compareTo(summary.getSavingsYield()));
        assertEquals(0, BigDecimal.valueOf(2700).compareTo(summary.getRemainingAmount()));
        assertEquals(LocalDate.of(2026, 6, 15), summary.getReferenceDate());
        assertEquals(6, summary.getMonth());
        assertEquals(2026, summary.getYear());
        assertSame(user, summary.getUser());
    }

}
