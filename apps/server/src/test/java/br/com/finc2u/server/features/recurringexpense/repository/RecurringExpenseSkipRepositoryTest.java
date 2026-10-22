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
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class RecurringExpenseSkipRepositoryTest {

    private static final LocalDate OCTOBER = LocalDate.of(2026, 10, 25);
    private static final LocalDate NOVEMBER = LocalDate.of(2026, 11, 25);

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
    void existsByRecurringExpenseIdAndDueDate_shouldMatchOnlyTheSameModelAndDueDate() {
        skipRepository.saveAndFlush(RecurringExpenseSkip.of(spotify, OCTOBER));

        assertTrue(skipRepository.existsByRecurringExpenseIdAndDueDate(spotify.getId(), OCTOBER));
        assertFalse(skipRepository.existsByRecurringExpenseIdAndDueDate(spotify.getId(), NOVEMBER));
        assertFalse(skipRepository.existsByRecurringExpenseIdAndDueDate(netflix.getId(), OCTOBER));
    }

    // O mesmo vencimento do mesmo modelo só pode ser pulado uma vez; outro mês ou outro modelo não conflita.
    @Test
    void save_shouldFailWithUniqueViolation_whenTheModelAlreadyHasThatDueDateSkipped() {
        skipRepository.saveAndFlush(RecurringExpenseSkip.of(spotify, OCTOBER));

        assertThrows(DataIntegrityViolationException.class,
            () -> skipRepository.saveAndFlush(RecurringExpenseSkip.of(spotify, OCTOBER)));
    }

    @Test
    void save_shouldAccept_whenTheDueDateOrTheModelIsDifferent() {
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

        assertFalse(skipRepository.existsByRecurringExpenseIdAndDueDate(spotify.getId(), OCTOBER));
        assertFalse(skipRepository.existsByRecurringExpenseIdAndDueDate(spotify.getId(), NOVEMBER));
        assertTrue(skipRepository.existsByRecurringExpenseIdAndDueDate(netflix.getId(), OCTOBER));
    }

    private RecurringExpense persistModel(String description, User user, Category category) {
        var model = RecurringExpense.of(description, BigDecimal.TEN, 25, YearMonth.of(2026, 1), null);
        model.setUser(user);
        model.setCategory(category);
        return testEntityManager.persist(model);
    }

}
