package br.com.finc2u.server.features.summary.integration;

import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.expense.vo.ExpenseCommand;
import br.com.finc2u.server.features.summary.service.MonthlySummaryService;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * O totalOverdue é um retrato: vale em relação à data em que o resumo foi calculado, e essa data fica gravada. Com um relógio que
 * anda, o teste mostra que ler o resumo não o atualiza e que recalcular grava a data nova junto do atraso novo.
 */
@SpringBootTest
class MonthlySummaryReferenceDateIntegrationTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDate DAY = LocalDate.of(2026, 10, 21);

    @TestConfiguration
    static class MovableClockConfig {

        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock();
        }

    }

    static class MovableClock extends Clock {

        private volatile LocalDate today = DAY;

        void setToday(LocalDate today) {
            this.today = today;
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return today.atStartOfDay(ZONE).toInstant();
        }

    }

    @Autowired
    private MovableClock clock;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private MonthlySummaryService monthlySummaryService;

    @BeforeEach
    void setUp() {
        clock.setToday(DAY);
    }

    @Test
    void recalculate_shouldStoreTheNewReferenceDateAndTheNewOverdue_whenTheClockMovedOn() {
        var userId = registerUser("referencia.recalcular@email.com");
        expense(userId, DAY.plusDays(3));
        var october = YearMonth.of(2026, 10);

        var first = monthlySummaryService.recalculateFrom(userId, october).summary();

        assertEquals(DAY, first.getReferenceDate());
        assertEquals(0, BigDecimal.ZERO.compareTo(first.getTotalOverdue()));

        clock.setToday(DAY.plusDays(5));
        var second = monthlySummaryService.recalculateFrom(userId, october).summary();

        assertEquals(DAY.plusDays(5), second.getReferenceDate());
        assertEquals(0, BigDecimal.valueOf(250).compareTo(second.getTotalOverdue()));
    }

    // Ler não recalcula: o GET devolve o retrato gravado, com a data em que ele foi tirado.
    @Test
    void get_shouldReturnTheStoredReferenceDate_evenWhenTheClockMovedOn() {
        var userId = registerUser("referencia.ler@email.com");
        expense(userId, DAY.plusDays(3));
        monthlySummaryService.recalculateFrom(userId, YearMonth.of(2026, 10));

        clock.setToday(DAY.plusDays(5));
        var read = monthlySummaryService.getByUserAndPeriod(userId, 10, 2026).summary();

        assertEquals(DAY, read.getReferenceDate());
        assertEquals(0, BigDecimal.ZERO.compareTo(read.getTotalOverdue()));
    }

    private void expense(UUID userId, LocalDate dueDate) {
        var expense = Expense.builder()
            .description("Conta")
            .value(BigDecimal.valueOf(250))
            .dueDate(dueDate)
            .expenseType(ExpenseType.VARIABLE)
            .paymentStatus(PaymentStatus.PENDING)
            .build();
        expenseService.create(userId, ExpenseCommand.of(expense).withCategoryId(categoryService.getByUser(userId).getFirst().getId()));
    }

    private UUID registerUser(String email) {
        var user = User.builder()
            .name("Ana")
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build();
        return userService.create(user, BigDecimal.valueOf(3000), LocalDate.of(2026, 1, 1)).getId();
    }

}
