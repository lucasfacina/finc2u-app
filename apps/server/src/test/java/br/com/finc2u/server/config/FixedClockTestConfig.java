package br.com.finc2u.server.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

// `Clock` fixo para os testes de controller (os slices `@WebMvcTest` não carregam o `ClockConfig`).
@TestConfiguration
public class FixedClockTestConfig {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 15, 10, 30);

    @Bean
    Clock clock() {
        return Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    }

}