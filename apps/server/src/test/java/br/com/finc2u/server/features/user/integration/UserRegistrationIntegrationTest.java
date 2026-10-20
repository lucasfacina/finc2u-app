package br.com.finc2u.server.features.user.integration;

import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.category.enums.DefaultCategory;
import br.com.finc2u.server.features.category.service.CategoryService;
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
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class UserRegistrationIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryService categoryService;

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

    @Test
    void create_shouldGenerateTheDefaultCategories() {
        var user = newUser("categorias.padrao@email.com");

        var savedUser = userService.create(user, BigDecimal.valueOf(3000), LocalDate.of(2026, 1, 1));

        var categories = categoryService.getByUser(savedUser.getId());
        var defaultNames = Arrays.stream(DefaultCategory.values())
            .map(DefaultCategory::getCategoryName)
            .toList();
        assertEquals(defaultNames.size(), categories.size());
        assertTrue(categories.stream().map(Category::getName).toList().containsAll(defaultNames));
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
