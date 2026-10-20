package br.com.finc2u.server.features.user.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserTest {

    // ---- savingsBalanceOrZero ----

    @Test
    void savingsBalanceOrZero_shouldReturnZero_whenSavingsBalanceIsNull() {
        User user = User.builder()
            .savingsBalance(null)
            .build();

        assertEquals(0, BigDecimal.ZERO.compareTo(user.savingsBalanceOrZero()));
    }

    @Test
    void savingsBalanceOrZero_shouldReturnTheInformedValue() {
        User user = User.builder()
            .savingsBalance(BigDecimal.valueOf(1500))
            .build();

        assertEquals(0, BigDecimal.valueOf(1500).compareTo(user.savingsBalanceOrZero()));
    }

}
