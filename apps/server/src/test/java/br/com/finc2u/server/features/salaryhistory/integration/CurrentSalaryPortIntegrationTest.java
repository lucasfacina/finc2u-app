package br.com.finc2u.server.features.salaryhistory.integration;

import br.com.finc2u.server.features.salaryhistory.service.SalaryHistoryService;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.port.CurrentSalaryPort;
import br.com.finc2u.server.features.user.projection.UserProfileProjection;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/*
 * A porta que o user declara, ligada de verdade: a feature do salário a implementa (o Spring injeta o SalaryHistoryService) e
 * o perfil do usuário mostra o salário vigente sem que o user conheça a feature do salário.
 */
@SpringBootTest
class CurrentSalaryPortIntegrationTest {

    @Autowired
    private CurrentSalaryPort currentSalaryPort;

    @Autowired
    private SalaryHistoryService salaryHistoryService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserProfileProjection userProfileProjection;

    @Test
    void port_shouldBeImplementedByTheSalaryHistoryService() {
        assertSame(salaryHistoryService, currentSalaryPort);
    }

    @Test
    void port_shouldServeTheCurrentSalaryOfOneAndOfSeveralUsers() {
        var ana = registerUser("porta.ana@email.com", 3000, LocalDate.of(2026, 1, 1));
        var beto = registerUser("porta.beto@email.com", 4500, LocalDate.of(2026, 2, 1));

        assertEquals(0, BigDecimal.valueOf(3000).compareTo(currentSalaryPort.getCurrentBaseSalary(ana).orElseThrow()));
        var salaries = currentSalaryPort.getCurrentBaseSalaries(List.of(ana, beto));
        assertEquals(0, BigDecimal.valueOf(3000).compareTo(salaries.get(ana)));
        assertEquals(0, BigDecimal.valueOf(4500).compareTo(salaries.get(beto)));
    }

    // Vigência no futuro: ainda não começou, então não há salário vigente e o usuário não aparece no mapa.
    @Test
    void port_shouldHaveNoCurrentSalary_whenTheFirstOneStartsInTheFuture() {
        var carla = registerUser("porta.carla@email.com", 3000, LocalDate.of(2999, 1, 1));

        assertTrue(currentSalaryPort.getCurrentBaseSalary(carla).isEmpty());
        assertFalse(currentSalaryPort.getCurrentBaseSalaries(List.of(carla)).containsKey(carla));
    }

    @Test
    void profile_shouldShowTheCurrentSalaryComingFromThePort() {
        var ana = registerUser("porta.perfil@email.com", 3200, LocalDate.of(2026, 1, 1));

        var profile = userProfileProjection.getProfile(ana);

        assertEquals(0, BigDecimal.valueOf(3200).compareTo(profile.baseSalary()));
    }

    private UUID registerUser(String email, int salary, LocalDate effectiveFrom) {
        var user = User.builder()
            .name("Ana")
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build();
        return userService.create(user, BigDecimal.valueOf(salary), effectiveFrom).getId();
    }

}
