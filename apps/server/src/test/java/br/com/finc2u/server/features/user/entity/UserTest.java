package br.com.finc2u.server.features.user.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    // ---- setUserConfiguration ----

    @Test
    void setUserConfiguration_shouldLinkConfigurationBackToUser() {
        User user = User.builder()
            .build();
        UserConfiguration configuration = UserConfiguration.builder()
            .build();

        user.setUserConfiguration(configuration);

        assertSame(configuration, user.getUserConfiguration());
        assertSame(user, configuration.getUser());
    }

    @Test
    void setUserConfiguration_shouldAcceptNull() {
        User user = User.builder()
            .build();

        user.setUserConfiguration(null);

        assertNull(user.getUserConfiguration());
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
