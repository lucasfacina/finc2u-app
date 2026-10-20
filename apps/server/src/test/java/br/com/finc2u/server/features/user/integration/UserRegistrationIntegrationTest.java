package br.com.finc2u.server.features.user.integration;

import br.com.finc2u.server.features.salaryhistory.entity.SalaryHistory;
import br.com.finc2u.server.features.salaryhistory.service.SalaryHistoryService;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.entity.UserConfiguration;
import br.com.finc2u.server.features.user.repository.UserRepository;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class UserRegistrationIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @MockitoSpyBean
    private SalaryHistoryService salaryHistoryService;

    @Test
    void create_shouldGenerateFirstSalaryHistory() {
        var user = newUser("primeira.vigencia@email.com");

        var savedUser = userService.create(user, BigDecimal.valueOf(3000), LocalDate.of(2026, 1, 1));

        var history = salaryHistoryService.getByUser(savedUser.getId());
        assertEquals(1, history.size());
        assertEquals(0, BigDecimal.valueOf(3000).compareTo(history.getFirst().getBaseSalary()));
        assertEquals(LocalDate.of(2026, 1, 1), history.getFirst().getEffectiveFrom());
    }

    @Test
    void create_shouldStartSalaryOnFirstDayOfCurrentMonth_whenEffectiveFromIsNotInformed() {
        var user = newUser("vigencia.padrao@email.com");

        var savedUser = userService.create(user, BigDecimal.valueOf(3000), null);

        var history = salaryHistoryService.getByUser(savedUser.getId());
        assertEquals(YearMonth.now().atDay(1), history.getFirst().getEffectiveFrom());
    }

    @Test
    void create_shouldNotSaveUser_whenFirstSalaryFails() {
        var user = newUser("rollback@email.com");
        doThrow(new IllegalStateException("falha ao gravar salário"))
            .when(salaryHistoryService).create(any(SalaryHistory.class), any());

        assertThrows(IllegalStateException.class,
            () -> userService.create(user, BigDecimal.valueOf(3000), LocalDate.of(2026, 1, 1)));

        assertTrue(userRepository.findByEmail("rollback@email.com").isEmpty());
    }

    private User newUser(String email) {
        var user = User.builder()
            .name("Ana")
            .email(email)
            .build();
        user.setUserConfiguration(UserConfiguration.builder()
            .savingsBalance(BigDecimal.valueOf(100))
            .build());
        return user;
    }

}
