package br.com.finc2u.server.features.user.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserRequestTest {

    @Test
    void toEntity_shouldBuildUserWithSavingsBalance() {
        var request = new UserRequest(
            "Lucas",
            "lucas@email.com",
            BigDecimal.valueOf(3000),
            BigDecimal.valueOf(500),
            null
        );

        var user = request.toEntity();

        assertEquals("Lucas", user.getName());
        assertEquals("lucas@email.com", user.getEmail());
        assertEquals(0, BigDecimal.valueOf(500).compareTo(user.getSavingsBalance()));
    }

}
