package br.com.finc2u.server.features.user.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class UserUpdateRequestTest {

    @Test
    void toEntity_shouldBuildUserWithOnlySavingsBalanceInTheConfiguration() {
        var request = new UserUpdateRequest("Lucas", "lucas@email.com", BigDecimal.valueOf(500));

        var user = request.toEntity();

        assertEquals("Lucas", user.getName());
        assertEquals("lucas@email.com", user.getEmail());
        assertNotNull(user.getUserConfiguration());
        assertEquals(0, BigDecimal.valueOf(500).compareTo(user.getUserConfiguration().getSavingsBalance()));
        assertSame(user, user.getUserConfiguration().getUser());
    }

}
