package br.com.finc2u.server.features.user.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserUpdateRequestTest {

    @Test
    void toEntity_shouldBuildUserWithNameEmailAndSavingsBalance() {
        var request = new UserUpdateRequest("Lucas", "lucas@email.com", BigDecimal.valueOf(500));

        var user = request.toEntity();

        assertEquals("Lucas", user.getName());
        assertEquals("lucas@email.com", user.getEmail());
        assertEquals(0, BigDecimal.valueOf(500).compareTo(user.getSavingsBalance()));
    }

}
