package br.com.finc2u.server.features.user.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserTest {

    // ---- baseSalaryOrZero ----

    @Test
    void baseSalaryOrZero_shouldReturnZero_whenNoConfiguration() {
        User user = User.builder()
                .build();

        assertEquals(0, BigDecimal.ZERO.compareTo(user.baseSalaryOrZero()));
    }

    @Test
    void baseSalaryOrZero_shouldReturnZero_whenBaseSalaryIsNull() {
        User user = User.builder()
                .userConfiguration(UserConfiguration.builder()
                        .baseSalary(null)
                        .build()
                )
                .build();

        assertEquals(0, BigDecimal.ZERO.compareTo(user.baseSalaryOrZero()));
    }

    @Test
    void baseSalaryOrZero_shouldReturnConfiguredValue() {
        User user = User.builder()
                .userConfiguration(UserConfiguration.builder()
                        .baseSalary(BigDecimal.valueOf(3000))
                        .build()
                )
                .build();

        assertEquals(0, BigDecimal.valueOf(3000).compareTo(user.baseSalaryOrZero()));
    }

    // ---- savingsBalanceOrZero ----

    @Test
    void savingsBalanceOrZero_shouldReturnZero_whenNoConfiguration() {
        User user = User.builder()
                .build();

        assertEquals(0, BigDecimal.ZERO.compareTo(user.savingsBalanceOrZero()));
    }

    @Test
    void savingsBalanceOrZero_shouldReturnZero_whenSavingsBalanceIsNull() {
        User user = User.builder()
                .userConfiguration(UserConfiguration.builder()
                        .savingsBalance(null)
                        .build()
                )
                .build();

        assertEquals(0, BigDecimal.ZERO.compareTo(user.savingsBalanceOrZero()));
    }

    @Test
    void savingsBalanceOrZero_shouldReturnConfiguredValue() {
        User user = User.builder()
                .userConfiguration(UserConfiguration.builder()
                        .savingsBalance(BigDecimal.valueOf(1500))
                        .build()
                )
                .build();

        assertEquals(0, BigDecimal.valueOf(1500).compareTo(user.savingsBalanceOrZero()));
    }

}