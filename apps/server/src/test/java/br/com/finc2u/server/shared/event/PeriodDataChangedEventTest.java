package br.com.finc2u.server.shared.event;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PeriodDataChangedEventTest {

    private final UUID userId = UUID.randomUUID();

    @Test
    void earliestOf_shouldUseTheMonthOfTheOnlyDate() {
        var event = PeriodDataChangedEvent.earliestOf(userId, LocalDate.of(2026, 8, 20));

        assertEquals(userId, event.userId());
        assertEquals(YearMonth.of(2026, 8), event.from());
    }

    // Um vencimento que muda de mês afeta a partir do mês mais antigo, venha ele primeiro ou depois.
    @Test
    void earliestOf_shouldUseTheEarliestMonth_regardlessOfTheOrder() {
        var earlierFirst = PeriodDataChangedEvent.earliestOf(userId, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 9, 10));
        var laterFirst = PeriodDataChangedEvent.earliestOf(userId, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 7, 10));

        assertEquals(YearMonth.of(2026, 7), earlierFirst.from());
        assertEquals(YearMonth.of(2026, 7), laterFirst.from());
    }

    @Test
    void earliestOf_shouldCompareAcrossYears() {
        var event = PeriodDataChangedEvent.earliestOf(userId, LocalDate.of(2027, 1, 5), LocalDate.of(2026, 12, 31));

        assertEquals(YearMonth.of(2026, 12), event.from());
    }

    @Test
    void earliestOf_shouldAcceptACollectionOfDates() {
        var event = PeriodDataChangedEvent.earliestOf(userId,
            List.of(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 8, 5), LocalDate.of(2026, 9, 5)));

        assertEquals(YearMonth.of(2026, 8), event.from());
    }

    @Test
    void earliestOf_shouldKeepTheMonth_whenTheDatesAreInTheSameMonth() {
        var event = PeriodDataChangedEvent.earliestOf(userId, LocalDate.of(2026, 7, 25), LocalDate.of(2026, 7, 1));

        assertEquals(YearMonth.of(2026, 7), event.from());
    }

    @Test
    void earliestOf_shouldFail_whenThereIsNoDate() {
        assertThrows(RuntimeException.class, () -> PeriodDataChangedEvent.earliestOf(userId, List.of()));
    }

}
