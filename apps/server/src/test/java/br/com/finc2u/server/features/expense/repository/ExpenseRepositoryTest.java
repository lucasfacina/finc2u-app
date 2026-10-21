package br.com.finc2u.server.features.expense.repository;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.filter.ExpenseFilter;
import br.com.finc2u.server.features.expense.form.ExpenseResponse;
import br.com.finc2u.server.features.expense.specification.ExpenseSpecification;
import br.com.finc2u.server.features.user.entity.User;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Trava o N+1 nas consultas de despesa que alimentam o ExpenseResponse (descrição, cartão e categoria). O cenário tem
 * vários cartões e várias categorias: com um único cartão/categoria o N+1 passaria despercebido. A contagem usa
 * getPrepareStatementCount() (SQL preparado, inclusive carregamentos preguiçosos), não getQueryExecutionCount().
 *
 * O total esperado das listagens é 2: a consulta de despesas (com cartão e categoria no @EntityGraph) mais um custo FIXO do
 * dono — o Expense.user é EAGER. Esse custo não cresce com os dados (toda consulta é escopada por um único usuário)
 * e some quando o Expense.user passar a ser LAZY; aí este número deve cair para 1. O que o teste realmente
 * protege é o crescimento: shouldNotGrowWithTheNumberOfCards.
 */
@DataJpaTest
class ExpenseRepositoryTest {

    private static final int CARDS = 3;
    private static final int EXPENSES_PER_CARD = 2;
    private static final int LIST_STATEMENTS = 2;
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;
    private User user;
    private UUID anyExpenseId;

    @BeforeEach
    void setUp() {
        user = persistUser();
        persistCardsWithExpenses(CARDS, 1);

        statistics = entityManager.getEntityManagerFactory()
            .unwrap(SessionFactory.class)
            .getStatistics();
        resetPersistenceContext();
    }

    @Test
    void findByUserId_shouldLoadCardAndCategoryInTheMainQuery() {
        var expenses = expenseRepository.findByUserId(user.getId());

        assertEquals(CARDS * EXPENSES_PER_CARD, expenses.size());
        expenses.forEach(expense -> ExpenseResponse.from(expense, TODAY));
        assertEquals(LIST_STATEMENTS, statistics.getPrepareStatementCount());
    }

    @Test
    void findAllBySpecification_shouldLoadCardInTheMainQuery() {
        var filter = new ExpenseFilter(
            null, // month
            null, // year
            null, // status
            null, // type
            null, // cardAccountId
            null, // categoryId
            null, // minPrice
            null, // maxPrice
            null // overdue
        );

        var expenses = expenseRepository.findAll(ExpenseSpecification.ofFilter(user.getId(), filter, TODAY));

        assertEquals(CARDS * EXPENSES_PER_CARD, expenses.size());
        expenses.forEach(expense -> ExpenseResponse.from(expense, TODAY));
        assertEquals(LIST_STATEMENTS, statistics.getPrepareStatementCount());
    }

    @Test
    void findFutureInstallments_shouldReturnOnlyTheUsersPendingParceledAfterTheReferenceDate_inOneQuery() {
        for (var number = 1; number <= 3; number++) {
            var cardAccount = persistCard("Parcelado " + number);
            var category = persistCategory("Parcelas " + number);
            persistParceled("Compra " + number + " 3/3", cardAccount, category, PaymentStatus.PENDING, TODAY.plusMonths(2), user);
            persistParceled("Compra " + number + " 2/3", cardAccount, category, PaymentStatus.PENDING, TODAY.plusMonths(1), user);
            persistParceled("Compra " + number + " paga", cardAccount, category, PaymentStatus.PAID, TODAY.plusMonths(1), user);
            persistParceled("Compra " + number + " vence hoje", cardAccount, category, PaymentStatus.PENDING, TODAY, user);
        }
        var other = testEntityManager.persist(User.builder().name("Bia").email("bia@email.com")
            .savingsBalance(BigDecimal.ZERO).build());
        var otherCategory = testEntityManager.persist(Category.builder().name("Outra").user(other).build());
        persistParceled("De outro usuário", null, otherCategory, PaymentStatus.PENDING, TODAY.plusMonths(1), other);
        resetPersistenceContext();

        var found = expenseRepository.findByUserIdAndExpenseTypeAndPaymentStatusAndDueDateAfterOrderByDueDate(
            user.getId(), ExpenseType.PARCELED, PaymentStatus.PENDING, TODAY);

        found.forEach(expense -> ExpenseResponse.from(expense, TODAY));
        assertEquals(LIST_STATEMENTS, statistics.getPrepareStatementCount());
        assertEquals(6, found.size());
        assertTrue(found.stream().allMatch(expense -> expense.getExpenseType() == ExpenseType.PARCELED
            && expense.getPaymentStatus() == PaymentStatus.PENDING
            && expense.getDueDate().isAfter(TODAY)
            && expense.getUser().getId().equals(user.getId())));
        assertEquals(found.stream().map(Expense::getDueDate).sorted().toList(),
            found.stream().map(Expense::getDueDate).toList());
    }

    @Test
    void findAllBySpecification_shouldFilterByCategory() {
        var categoryId = expenseRepository.findById(anyExpenseId).orElseThrow().getCategory().getId();
        resetPersistenceContext();
        var filter = new ExpenseFilter(
            null, // month
            null, // year
            null, // status
            null, // type
            null, // cardAccountId
            categoryId,
            null, // minPrice
            null, // maxPrice
            null // overdue
        );

        var expenses = expenseRepository.findAll(ExpenseSpecification.ofFilter(user.getId(), filter, TODAY));

        assertEquals(EXPENSES_PER_CARD, expenses.size());
        assertTrue(expenses.stream().allMatch(expense -> expense.getCategory().getId().equals(categoryId)));
    }

    @Test
    void findAllBySpecification_shouldReturnOnlyPendingExpensesDueBeforeTheReferenceDate_whenOverdue() {
        var cardAccount = persistCard("Cartão atraso");
        var category = persistCategory("Categoria atraso");
        var overdue = persistExpense("Atrasada", cardAccount, category, PaymentStatus.PENDING, TODAY.minusDays(1));
        persistExpense("Vence hoje", cardAccount, category, PaymentStatus.PENDING, TODAY);
        persistExpense("Paga vencida", cardAccount, category, PaymentStatus.PAID, TODAY.minusDays(10));
        resetPersistenceContext();
        var filter = new ExpenseFilter(
            null, // month
            null, // year
            null, // status
            null, // type
            null, // cardAccountId
            null, // categoryId
            null, // minPrice
            null, // maxPrice
            true // overdue
        );

        var expenses = expenseRepository.findAll(ExpenseSpecification.ofFilter(user.getId(), filter, TODAY));

        assertEquals(1, expenses.size());
        assertEquals(overdue.getId(), expenses.getFirst().getId());
    }

    @Test
    void findAllBySpecification_shouldNotRestrictByOverdue_whenFalseOrNotInformed() {
        var cardAccount = persistCard("Cartão atraso");
        var category = persistCategory("Categoria atraso");
        persistExpense("Atrasada", cardAccount, category, PaymentStatus.PENDING, TODAY.minusDays(1));
        resetPersistenceContext();
        var filter = new ExpenseFilter(null, null, null, null, null, null, null, null, false);

        var expenses = expenseRepository.findAll(ExpenseSpecification.ofFilter(user.getId(), filter, TODAY));

        assertEquals(CARDS * EXPENSES_PER_CARD + 1, expenses.size());
    }

    @Test
    void findByInstallmentGroupId_shouldReturnOnlyThatPurchaseOrderedByInstallment_inOneQuery() {
        var cardAccount = persistCard("Cartão do grupo");
        var category = persistCategory("Categoria do grupo");
        var groupId = UUID.randomUUID();
        for (var installment : List.of(3, 1, 2)) {
            persistParceled("Compra " + installment + "/3", cardAccount, category, PaymentStatus.PENDING,
                TODAY.plusMonths(installment), user, groupId, installment);
        }
        persistParceled("Outra compra", cardAccount, category, PaymentStatus.PENDING, TODAY.plusMonths(1), user,
            UUID.randomUUID(), 1);
        resetPersistenceContext();

        var found = expenseRepository.findByInstallmentGroupIdOrderByCurrentInstallment(groupId);

        found.forEach(expense -> ExpenseResponse.from(expense, TODAY));
        assertEquals(LIST_STATEMENTS, statistics.getPrepareStatementCount());
        assertEquals(List.of(1, 2, 3), found.stream().map(Expense::getCurrentInstallment).toList());
        assertTrue(found.stream().allMatch(expense -> groupId.equals(expense.getInstallmentGroupId())));
    }

    @Test
    void findByInstallmentGroupId_shouldBeEmpty_whenTheGroupDoesNotExist() {
        assertTrue(expenseRepository.findByInstallmentGroupIdOrderByCurrentInstallment(UUID.randomUUID()).isEmpty());
    }

    @Test
    void shouldPersistTheInstallmentGroupId() {
        var groupId = UUID.randomUUID();
        var saved = testEntityManager.persist(Expense.builder()
            .description("Notebook")
            .value(BigDecimal.valueOf(500))
            .dueDate(LocalDate.of(2026, 7, 10))
            .expenseType(ExpenseType.PARCELED)
            .paymentStatus(PaymentStatus.PENDING)
            .currentInstallment(2)
            .totalInstallment(10)
            .installmentGroupId(groupId)
            .user(user)
            .category(persistCategory("Compras"))
            .build());
        resetPersistenceContext();

        var found = expenseRepository.findById(saved.getId()).orElseThrow();

        assertEquals(groupId, found.getInstallmentGroupId());
    }

    @Test
    void shouldLeaveTheInstallmentGroupIdEmpty_whenTheExpenseIsNotParceled() {
        var found = expenseRepository.findById(anyExpenseId).orElseThrow();

        assertNull(found.getInstallmentGroupId());
    }

    @Test
    void findByUserIdAndDueDateBetween_shouldLoadCardInTheMainQuery() {
        var expenses = expenseRepository.findByUserIdAndDueDateBetween(
            user.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertEquals(CARDS * EXPENSES_PER_CARD, expenses.size());
        expenses.forEach(expense -> assertNotNull(expense.getCardAccount().getCardName()));
        assertEquals(LIST_STATEMENTS, statistics.getPrepareStatementCount());
    }

    @Test
    void findById_shouldLoadCardAndCategoryInASingleQuery() {
        var expense = expenseRepository.findById(anyExpenseId).orElseThrow();

        ExpenseResponse.from(expense, TODAY);
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    @Test
    void shouldNotGrowWithTheNumberOfCards() {
        expenseRepository.findByUserId(user.getId()).forEach(expense -> ExpenseResponse.from(expense, TODAY));
        var statementsWithFewCards = statistics.getPrepareStatementCount();

        persistCardsWithExpenses(CARDS * 3, CARDS + 1);
        resetPersistenceContext();
        expenseRepository.findByUserId(user.getId()).forEach(expense -> ExpenseResponse.from(expense, TODAY));

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
            var category = persistCategory("Categoria " + card);
            for (int i = 1; i <= EXPENSES_PER_CARD; i++) {
                var expense = persistExpense("Despesa " + card + "." + i, cardAccount, category);
                anyExpenseId = expense.getId();
            }
        }
    }

    private User persistUser() {
        var newUser = User.builder()
            .name("Ana")
            .email("ana@email.com")
            .savingsBalance(BigDecimal.valueOf(100))
            .build();
        return testEntityManager.persist(newUser);
    }

    private Category persistCategory(String name) {
        return testEntityManager.persist(Category.builder()
            .name(name)
            .user(user)
            .build());
    }

    private CardAccount persistCard(String name) {
        return testEntityManager.persist(CardAccount.builder()
            .cardName(name)
            .closingDate(10)
            .dueDate(17)
            .user(user)
            .build());
    }

    private Expense persistParceled(String description, CardAccount cardAccount, Category category,
                                    PaymentStatus paymentStatus, LocalDate dueDate, User owner) {
        return persistParceled(description, cardAccount, category, paymentStatus, dueDate, owner, UUID.randomUUID(), 2);
    }

    private Expense persistParceled(String description, CardAccount cardAccount, Category category,
                                    PaymentStatus paymentStatus, LocalDate dueDate, User owner, UUID groupId,
                                    int currentInstallment) {
        return testEntityManager.persist(Expense.builder()
            .description(description)
            .value(BigDecimal.valueOf(50))
            .dueDate(dueDate)
            .expenseType(ExpenseType.PARCELED)
            .paymentStatus(paymentStatus)
            .currentInstallment(currentInstallment)
            .totalInstallment(3)
            .installmentGroupId(groupId)
            .user(owner)
            .cardAccount(cardAccount)
            .category(category)
            .build());
    }

    private Expense persistExpense(String description, CardAccount cardAccount, Category category) {
        return persistExpense(description, cardAccount, category, PaymentStatus.PENDING, LocalDate.of(2026, 6, 17));
    }

    private Expense persistExpense(String description, CardAccount cardAccount, Category category,
                                   PaymentStatus paymentStatus, LocalDate dueDate) {
        return testEntityManager.persist(Expense.builder()
            .description(description)
            .value(BigDecimal.valueOf(50))
            .dueDate(dueDate)
            .expenseType(ExpenseType.VARIABLE)
            .paymentStatus(paymentStatus)
            .user(user)
            .cardAccount(cardAccount)
            .category(category)
            .build());
    }

}
