package br.com.finc2u.server.config;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

    class ClockConfigTest {

    @Test
    void shouldUseSystemDefaultZone() {
        var clock = new ClockConfig().clock();

        assertThat(clock.getZone()).isEqualTo(ZoneId.systemDefault());
    }

}
