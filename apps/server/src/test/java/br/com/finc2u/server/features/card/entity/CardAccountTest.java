package br.com.finc2u.server.features.card.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CardAccountTest {

    @Test
    void calculateInvoiceDueDate_shouldFallInSameClosingMonth_whenPurchaseUpToClosingDay() {
        // Fecha dia 5, vence dia 15. Compra em 03/05 (<= 5) → fatura fecha em maio, vence 15/05.
        CardAccount card = CardAccount.builder()
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
        CardAccount card = CardAccount.builder()
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
        CardAccount card = CardAccount.builder()
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
        CardAccount card = CardAccount.builder()
                .closingDate(5)
                .dueDate(31)
                .build();

        assertEquals(LocalDate.of(2026, 2, 28),
                card.calculateInvoiceDueDate(LocalDate.of(2026, 1, 10))
        );
    }

}
