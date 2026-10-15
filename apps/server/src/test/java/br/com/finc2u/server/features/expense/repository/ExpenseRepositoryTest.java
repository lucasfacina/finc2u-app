package br.com.finc2u.server.features.expense.repository;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.filter.ExpenseFilter;
import br.com.finc2u.server.features.expense.form.ExpenseResponse;
import br.com.finc2u.server.features.expense.specification.ExpenseSpecification;
import br.com.finc2u.server.features.tag.entity.Tag;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.entity.UserConfiguration;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * Trava o N+1 nas consultas de despesa que alimentam o ExpenseResponse (descrição, cartão e tags). O cenário tem
 * vários cartões e várias tags: com um único cartão/tag o N+1 passaria despercebido. A contagem usa
 * getPrepareStatementCount() (todo SQL preparado, inclusive carregamentos preguiçosos), não getQueryExecutionCount().
 *
 * O total esperado das listagens é 2: a consulta de despesas (com tags e cartão no @EntityGraph) mais um custo FIXO do
 * dono — o Expense.user é EAGER e o User.userConfiguration (1:1 inverso) é carregado à parte. Esse custo não cresce
 * com os dados (toda consulta é escopada por um único usuário) e some quando o Expense.user virar LAZY (Fase 11); aí
 * este número deve cair para 1. O que o teste realmente protege é o crescimento: shouldNotGrowWithTheNumberOfCards.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ExpenseRepositoryTest {

    private static final int CARDS = 3;
    private static final int EXPENSES_PER_CARD = 2;
    private static final int LIST_STATEMENTS = 2;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;
    private User user;
    private UUID anyExpenseId;
    private List<Tag> tags;

    @BeforeEach
    void setUp() {
        user = persistUser();
        tags = List.of(persistTag("Alimentação"), persistTag("Lazer"), persistTag("Assinaturas"));
        persistCardsWithExpenses(CARDS, 1);

        statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        resetPersistenceContext();
    }

    @Test
    void findByUserId_shouldLoadCardAndTagsInTheMainQuery() {
        var expenses = expenseRepository.findByUserId(user.getId());

        assertEquals(CARDS * EXPENSES_PER_CARD, expenses.size());
        expenses.forEach(ExpenseResponse::from);
        assertEquals(LIST_STATEMENTS, statistics.getPrepareStatementCount());
    }

    @Test
    void findAllBySpecification_shouldLoadCardAndTagsInTheMainQuery() {
        var filter = new ExpenseFilter(null, null, null, null, null, null, null);

        var expenses = expenseRepository.findAll(ExpenseSpecification.ofFilter(user.getId(), filter));

        assertEquals(CARDS * EXPENSES_PER_CARD, expenses.size());
        expenses.forEach(ExpenseResponse::from);
        assertEquals(LIST_STATEMENTS, statistics.getPrepareStatementCount());
    }

    @Test
    void findByExpenseTypeAndUserId_shouldLoadCardAndTagsInTheMainQuery() {
        var expenses = expenseRepository.findByExpenseTypeAndUserId(ExpenseType.VARIABLE, user.getId());

        assertEquals(CARDS * EXPENSES_PER_CARD, expenses.size());
        expenses.forEach(ExpenseResponse::from);
        assertEquals(LIST_STATEMENTS, statistics.getPrepareStatementCount());
    }

    @Test
    void findByUserIdAndDueDateBetween_shouldLoadCardInTheMainQuery() {
        var expenses = expenseRepository.findByUserIdAndDueDateBetween(
                user.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertEquals(CARDS * EXPENSES_PER_CARD, expenses.size());
        expenses.forEach(expense -> expense.getCardAccount().getCardName());
        assertEquals(LIST_STATEMENTS, statistics.getPrepareStatementCount());
    }

    @Test
    void findById_shouldLoadCardAndTagsInASingleQuery() {
        var expense = expenseRepository.findById(anyExpenseId).orElseThrow();

        ExpenseResponse.from(expense);
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    @Test
    void shouldNotGrowWithTheNumberOfCards() {
        expenseRepository.findByUserId(user.getId()).forEach(ExpenseResponse::from);
        var statementsWithFewCards = statistics.getPrepareStatementCount();

        persistCardsWithExpenses(CARDS * 3, CARDS + 1);
        resetPersistenceContext();
        expenseRepository.findByUserId(user.getId()).forEach(ExpenseResponse::from);

        assertEquals(statementsWithFewCards, statistics.getPrepareStatementCount(),
                "Mais cartões não podem gerar mais statements (N+1)");
    }

    private void resetPersistenceContext() {
        testEntityManager.flush();
        testEntityManager.clear();
        statistics.clear();
    }

    private void persistCardsWithExpenses(int cards, int firstCardNumber) {
        for (int card = firstCardNumber; card < firstCardNumber + cards; card++) {
            var cardAccount = persistCard("Cartão " + card);
            for (int i = 1; i <= EXPENSES_PER_CARD; i++) {
                var expense = persistExpense("Despesa " + card + "." + i, cardAccount);
                anyExpenseId = expense.getId();
            }
        }
    }

    private User persistUser() {
        var newUser = User.builder()
                .name("Ana")
                .email("ana@email.com")
                .build();
        newUser.setUserConfiguration(UserConfiguration.builder()
                .baseSalary(BigDecimal.valueOf(3000))
                .savingsBalance(BigDecimal.valueOf(100))
                .build());
        return testEntityManager.persist(newUser);
    }

    private Tag persistTag(String name) {
        return testEntityManager.persist(Tag.of(name));
    }

    private CardAccount persistCard(String name) {
        return testEntityManager.persist(CardAccount.builder()
                .cardName(name)
                .closingDate(10)
                .dueDate(17)
                .user(user)
                .build());
    }

    private Expense persistExpense(String description, CardAccount cardAccount) {
        return testEntityManager.persist(Expense.builder()
                .description(description)
                .value(BigDecimal.valueOf(50))
                .dueDate(LocalDate.of(2026, 6, 17))
                .expenseType(ExpenseType.VARIABLE)
                .paymentStatus(PaymentStatus.PENDING)
                .user(user)
                .cardAccount(cardAccount)
                .tags(new ArrayList<>(tags.subList(0, 2)))
                .build());
    }

}
