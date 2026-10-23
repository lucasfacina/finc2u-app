package br.com.finc2u.server.features.spreadsheetimport.vo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InstallmentMarkTest {

    @Test
    void isValid_shouldAcceptFromOneUpToTheTotal() {
        assertTrue(InstallmentMark.isValid(1, 1));
        assertTrue(InstallmentMark.isValid(12, 12));
        assertFalse(InstallmentMark.isValid(0, 12));
        assertFalse(InstallmentMark.isValid(13, 12));
    }

    @Test
    void of_shouldRejectAnInvalidInstallment() {
        var mark = InstallmentMark.of(3, 10);

        assertEquals(3, mark.number());
        assertEquals(10, mark.total());
        assertThrows(IllegalArgumentException.class, () -> InstallmentMark.of(11, 10));
    }

}
