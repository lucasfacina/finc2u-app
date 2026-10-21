package br.com.finc2u.server.features.recurringexpense.repository;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.user.entity.User;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/*
 * A listagem alimenta o Response (categoria e cartão). O cenário tem vários cartões e categorias: com um só o N+1
 * passaria despercebido. A contagem usa getPrepareStatementCount() (SQL preparado, inclusive carregamentos preguiçosos).
 *
 * O total esperado é 2: a consulta dos modelos (com cartão e categoria no @EntityGraph) mais um custo FIXO do dono dos
 * cartões — o CardAccount.user é EAGER e toda consulta é escopada por um único usuário, então o custo não cresce com os
 * dados. O que o teste protege é o crescimento: o total é o mesmo com 3 cartões e categorias.
 */
@DataJpaTest
class RecurringExpenseRepositoryTest {

    private static final int MODELS = 3;
    private static final int LIST_STATEMENTS = 2;

    @Autowired
    private RecurringExpenseRepository recurringExpenseRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private EntityManager entityManager;

    private User ana;
    private User beto;

    @BeforeEach
    void setUp() {
        ana = persistUser("Ana", "ana@email.com");
        beto = persistUser("Beto", "beto@email.com");
    }

    @Test
    void findByUserIdOrderByDescriptionAsc_shouldReturnOnlyModelsOfTheUser_sortedByDescription() {
        var category = persistCategory("Assinaturas", ana);
        persistModel("Spotify", ana, category, null);
        persistModel("Netflix", ana, category, null);
        persistModel("Aluguel", beto, persistCategory("Moradia", beto), null);

        var models = recurringExpenseRepository.findByUserIdOrderByDescriptionAsc(ana.getId());

        assertEquals(List.of("Netflix", "Spotify"), models.stream().map(RecurringExpense::getDescription).toList());
    }

    @Test
    void findByUserIdOrderByDescriptionAsc_shouldNotGrowWithTheNumberOfCardsAndCategories() {
        for (int i = 1; i <= MODELS; i++) {
            persistModel("Modelo " + i, ana, persistCategory("Categoria " + i, ana), persistCard("Cartão " + i, ana));
        }
        var statistics = entityManager.getEntityManagerFactory()
            .unwrap(SessionFactory.class)
            .getStatistics();
        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        var models = recurringExpenseRepository.findByUserIdOrderByDescriptionAsc(ana.getId());
        models.forEach(model -> {
            assertNotNull(model.getCategory().getName());
            assertNotNull(model.getCardAccount().getCardName());
        });

        assertEquals(MODELS, models.size());
        assertEquals(LIST_STATEMENTS, statistics.getPrepareStatementCount());
    }

    // O Response lê a categoria (LAZY) fora da transação: o findById tem que trazê-la numa consulta só, com o cartão.
    @Test
    void findById_shouldLoadTheCategoryAndTheCardInASingleStatement() {
        var model = persistModel("Spotify", ana, persistCategory("Assinaturas", ana), null);
        var statistics = entityManager.getEntityManagerFactory()
            .unwrap(SessionFactory.class)
            .getStatistics();
        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        var found = recurringExpenseRepository.findById(model.getId()).orElseThrow();

        assertTrue(Hibernate.isInitialized(found.getCategory()));
        assertEquals("Assinaturas", found.getCategory().getName());
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    @Test
    void save_shouldKeepTheMonthsAsTheFirstDay_andAcceptNoEndMonthAndNoCard() {
        var model = RecurringExpense.of("Spotify", BigDecimal.valueOf(21.90), 31, YearMonth.of(2026, 10), null);
        model.setUser(ana);
        model.setCategory(persistCategory("Assinaturas", ana));
        var id = recurringExpenseRepository.saveAndFlush(model).getId();
        entityManager.clear();

        var saved = recurringExpenseRepository.findById(id).orElseThrow();

        assertEquals(LocalDate.of(2026, 10, 1), saved.getStartMonth());
        assertNull(saved.getEndMonth());
        assertNull(saved.getCardAccount());
        assertEquals(31, saved.getDayOfMonth());
    }

    private User persistUser(String name, String email) {
        var user = User.builder()
            .name(name)
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build();
        return testEntityManager.persist(user);
    }

    private Category persistCategory(String name, User user) {
        return testEntityManager.persist(Category.builder()
            .name(name)
            .user(user)
            .build());
    }

    private CardAccount persistCard(String name, User user) {
        return testEntityManager.persist(CardAccount.builder()
            .cardName(name)
            .closingDate(10)
            .dueDate(17)
            .user(user)
            .build());
    }

    private RecurringExpense persistModel(String description, User user, Category category, CardAccount card) {
        var model = RecurringExpense.of(description, BigDecimal.TEN, 5, YearMonth.of(2026, 10), YearMonth.of(2027, 3));
        model.setUser(user);
        model.setCategory(category);
        model.setCardAccount(card);
        return testEntityManager.persist(model);
    }

}
