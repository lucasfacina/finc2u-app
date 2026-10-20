package br.com.finc2u.server.features.card.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CardAccountRequestTest {

    @Test
    void toEntity_shouldStoreTheLastDayOfTheExpirationMonth() {
        var request = new CardAccountRequest("Nubank", 5, 15, YearMonth.of(2030, 2));

        var card = request.toEntity();

        assertEquals("Nubank", card.getCardName());
        assertEquals(LocalDate.of(2030, 2, 28), card.getExpirationDate());
    }

    @Test
    void toEntity_shouldLeaveExpirationEmpty_whenNotInformed() {
        var request = new CardAccountRequest("Nubank", 5, 15, null);

        assertNull(request.toEntity().getExpirationDate());
    }

}
