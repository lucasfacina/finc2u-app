package br.com.finc2u.server.shared.vo;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class MonthlyPeriodTest {

    @Test
    void of_shouldThrow_whenMonthIsZero() {
        assertThrows(IllegalArgumentException.class, () -> MonthlyPeriod.of(0, 2026));
    }

    @Test
    void of_shouldThrow_whenMonthIsThirteen() {
        assertThrows(IllegalArgumentException.class, () -> MonthlyPeriod.of(13, 2026));
    }

    @Test
    void of_shouldCreate_whenMonthIsValid() {
        MonthlyPeriod period = MonthlyPeriod.of(6, 2026);
        assertEquals(6, period.month());
        assertEquals(2026, period.year());
    }

    @Test
    void start_shouldReturnFirstDayOfMonth() {
        assertEquals(LocalDate.of(2026, 3, 1), MonthlyPeriod.of(3, 2026).start());
    }

    @Test
    void end_shouldReturnLastDayOfMonth() {
        assertEquals(LocalDate.of(2026, 3, 31), MonthlyPeriod.of(3, 2026).end());
    }

    @Test
    void end_shouldReturn29_whenFebruaryOnLeapYear() {
        assertEquals(LocalDate.of(2024, 2, 29), MonthlyPeriod.of(2, 2024).end());
    }

    @Test
    void end_shouldReturn28_whenFebruaryOnNonLeapYear() {
        assertEquals(LocalDate.of(2026, 2, 28), MonthlyPeriod.of(2, 2026).end());
    }

    @Test
    void previous_shouldReturnPriorMonth() {
        MonthlyPeriod prev = MonthlyPeriod.of(6, 2026).previous();
        assertEquals(5, prev.month());
        assertEquals(2026, prev.year());
    }

    @Test
    void previous_fromJanuary_shouldReturnDecemberOfPreviousYear() {
        MonthlyPeriod prev = MonthlyPeriod.of(1, 2026).previous();
        assertEquals(12, prev.month());
        assertEquals(2025, prev.year());
    }

}