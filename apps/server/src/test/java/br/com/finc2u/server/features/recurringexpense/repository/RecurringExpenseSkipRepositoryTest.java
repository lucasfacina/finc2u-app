package br.com.finc2u.server.features.recurringexpense.repository;

import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpenseSkip;
import br.com.finc2u.server.features.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class RecurringExpenseSkipRepositoryTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    @Autowired
    private RecurringExpenseSkipRepository skipRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private EntityManager entityManager;

    private RecurringExpense spotify;
    private RecurringExpense netflix;

    @BeforeEach
    void setUp() {
        var ana = testEntityManager.persist(User.builder()
            .name("Ana")
            .email("ana@email.com")
            .savingsBalance(BigDecimal.valueOf(100))
            .build());
        var category = testEntityManager.persist(Category.builder()
            .name("Assinaturas")
            .user(ana)
            .build());
        spotify = persistModel("Spotify", ana, category);
        netflix = persistModel("Netflix", ana, category);
    }

    @Test
    void existsByRecurringExpenseIdAndRecurrenceMonth_shouldMatchOnlyTheSameModelAndMonth() {
        skipRepository.saveAndFlush(RecurringExpenseSkip.of(spotify, OCTOBER));

        assertTrue(skipRepository.existsByRecurringExpenseIdAndRecurrenceMonth(spotify.getId(), OCTOBER.atDay(1)));
        assertFalse(skipRepository.existsByRecurringExpenseIdAndRecurrenceMonth(spotify.getId(), NOVEMBER.atDay(1)));
        assertFalse(skipRepository.existsByRecurringExpenseIdAndRecurrenceMonth(netflix.getId(), OCTOBER.atDay(1)));
    }

    // O mesmo mês do mesmo modelo só pode ser pulado uma vez; outro mês ou outro modelo não conflita.
    @Test
    void save_shouldFailWithUniqueViolation_whenTheModelAlreadyHasThatMonthSkipped() {
        skipRepository.saveAndFlush(RecurringExpenseSkip.of(spotify, OCTOBER));

        assertThrows(DataIntegrityViolationException.class,
            () -> skipRepository.saveAndFlush(RecurringExpenseSkip.of(spotify, OCTOBER)));
    }

    @Test
    void save_shouldAccept_whenTheMonthOrTheModelIsDifferent() {
        skipRepository.saveAndFlush(RecurringExpenseSkip.of(spotify, OCTOBER));

        assertDoesNotThrow(() -> {
            skipRepository.saveAndFlush(RecurringExpenseSkip.of(spotify, NOVEMBER));
            skipRepository.saveAndFlush(RecurringExpenseSkip.of(netflix, OCTOBER));
        });
    }

    @Test
    void deleteByRecurringExpenseId_shouldRemoveOnlyTheSkipsOfThatModel() {
        skipRepository.saveAndFlush(RecurringExpenseSkip.of(spotify, OCTOBER));
        skipRepository.saveAndFlush(RecurringExpenseSkip.of(spotify, NOVEMBER));
        skipRepository.saveAndFlush(RecurringExpenseSkip.of(netflix, OCTOBER));

        skipRepository.deleteByRecurringExpenseId(spotify.getId());
        entityManager.clear();

        assertFalse(skipRepository.existsByRecurringExpenseIdAndRecurrenceMonth(spotify.getId(), OCTOBER.atDay(1)));
        assertFalse(skipRepository.existsByRecurringExpenseIdAndRecurrenceMonth(spotify.getId(), NOVEMBER.atDay(1)));
        assertTrue(skipRepository.existsByRecurringExpenseIdAndRecurrenceMonth(netflix.getId(), OCTOBER.atDay(1)));
    }

    private RecurringExpense persistModel(String description, User user, Category category) {
        var model = RecurringExpense.of(description, BigDecimal.TEN, 25, YearMonth.of(2026, 1), null);
        model.setUser(user);
        model.setCategory(category);
        return testEntityManager.persist(model);
    }

}
