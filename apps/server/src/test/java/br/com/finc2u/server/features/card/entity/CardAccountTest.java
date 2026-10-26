package br.com.finc2u.server.features.card.entity;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

public class CardAccountTest {

    // ---- of ----

    @Test
    void of_shouldFillTheFieldsAndStoreTheLastDayOfTheExpirationMonth() {
        var card = CardAccount.of("Nubank", 5, 15, YearMonth.of(2028, 2));

        assertEquals("Nubank", card.getCardName());
        assertEquals(5, card.getClosingDate());
        assertEquals(15, card.getDueDate());
        assertEquals(LocalDate.of(2028, 2, 29), card.getExpirationDate());
    }

    @Test
    void of_shouldLeaveExpirationEmpty_whenNotInformed() {
        assertNull(CardAccount.of("Nubank", 5, 15, null).getExpirationDate());
    }

    @Test
    void calculateInvoiceDueDate_shouldFallInSameClosingMonth_whenPurchaseUpToClosingDay() {
        // Fecha dia 5, vence dia 15. Compra em 03/05 (<= 5) → fatura fecha em maio, vence 15/05.
        var card = CardAccount.builder()
            .closingDate(5)
            .dueDate(15)
            .build();

        assertEquals(LocalDate.of(2026, 5, 15),
            card.calculateInvoiceDueDate(LocalDate.of(2026, 5, 3))
        );
    }

    @Test
    void calculateInvoiceDueDate_shouldRollToNextMonth_whenPurchaseAfterClosingDay() {
        // Fecha dia 5, vence dia 15. Compra em 10/05 (> 5) → fatura fecha em junho, vence 15/06.
        var card = CardAccount.builder()
            .closingDate(5)
            .dueDate(15)
            .build();

        assertEquals(LocalDate.of(2026, 6, 15),
            card.calculateInvoiceDueDate(LocalDate.of(2026, 5, 10))
        );
    }

    @Test
    void calculateInvoiceDueDate_shouldDueMonthAfterClosing_whenDueDayBeforeClosingDay() {
        // Fecha dia 30, vence dia 15. Compra em 20/05 (<= 30) → fecha em maio, vence no mês seguinte: 15/06.
        var card = CardAccount.builder()
            .closingDate(30)
            .dueDate(15)
            .build();

        assertEquals(LocalDate.of(2026, 6, 15),
            card.calculateInvoiceDueDate(LocalDate.of(2026, 5, 20))
        );
    }

    @Test
    void calculateInvoiceDueDate_shouldClampToLastDayOfMonth_whenDueDayExceedsMonthLength() {
        // Vence dia 31, mas a fatura cai em fevereiro → ajusta para o último dia do mês (28).
        var card = CardAccount.builder()
            .closingDate(5)
            .dueDate(31)
            .build();

        assertEquals(LocalDate.of(2026, 2, 28),
            card.calculateInvoiceDueDate(LocalDate.of(2026, 1, 10))
        );
    }

    // ---- assertUsableOn ----

    @Test
    void assertUsableOn_shouldAccept_whenCardHasNoExpiration() {
        var card = CardAccount.builder()
            .build();

        assertDoesNotThrow(() -> card.assertUsableOn(LocalDate.of(2099, 12, 31)));
    }

    @Test
    void assertUsableOn_shouldAccept_whenPurchaseIsBeforeTheExpirationMonth() {
        var card = CardAccount.builder()
            .expirationDate(LocalDate.of(2030, 5, 31))
            .build();

        assertDoesNotThrow(() -> card.assertUsableOn(LocalDate.of(2030, 4, 10)));
    }

    @Test
    void assertUsableOn_shouldAccept_whenPurchaseIsOnTheLastDayOfTheExpirationMonth() {
        var card = CardAccount.builder()
            .expirationDate(LocalDate.of(2030, 5, 31))
            .build();

        assertDoesNotThrow(() -> card.assertUsableOn(LocalDate.of(2030, 5, 31)));
    }

    @Test
    void assertUsableOn_shouldThrowBusinessException_whenPurchaseIsInTheMonthAfterTheExpiration() {
        var card = CardAccount.builder()
            .expirationDate(LocalDate.of(2030, 5, 31))
            .build();

        var exception = assertThrows(BusinessException.class,
            () -> card.assertUsableOn(LocalDate.of(2030, 6, 1)));

        assertEquals("Cartão expirado em 05/2030.", exception.getMessage());
    }

    // ---- assertExpirationNotInThePast ----

    @Test
    void assertExpirationNotInThePast_shouldAccept_whenThereIsNoExpiration() {
        var card = CardAccount.of("Nubank", 5, 15, null);

        assertDoesNotThrow(() -> card.assertExpirationNotInThePast(LocalDate.of(2026, 6, 15)));
    }

    // O mês corrente vale, inclusive no último dia dele.
    @Test
    void assertExpirationNotInThePast_shouldAccept_whenTheExpirationIsInTheCurrentMonthOrLater() {
        var current = CardAccount.of("Nubank", 5, 15, YearMonth.of(2026, 6));
        var later = CardAccount.of("Nubank", 5, 15, YearMonth.of(2030, 1));

        assertDoesNotThrow(() -> current.assertExpirationNotInThePast(LocalDate.of(2026, 6, 15)));
        assertDoesNotThrow(() -> current.assertExpirationNotInThePast(LocalDate.of(2026, 6, 30)));
        assertDoesNotThrow(() -> later.assertExpirationNotInThePast(LocalDate.of(2026, 6, 15)));
    }

    @Test
    void assertExpirationNotInThePast_shouldThrowInvalidRequest_whenTheExpirationMonthAlreadyPassed() {
        var card = CardAccount.of("Nubank", 5, 15, YearMonth.of(2026, 5));

        assertThrows(InvalidRequestException.class, () -> card.assertExpirationNotInThePast(LocalDate.of(2026, 6, 1)));
    }

}
