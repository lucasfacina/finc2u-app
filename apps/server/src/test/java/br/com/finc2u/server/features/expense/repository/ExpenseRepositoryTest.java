package br.com.finc2u.server.features.expense.repository;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.filter.ExpenseFilter;
import br.com.finc2u.server.features.expense.form.ExpenseResponse;
import br.com.finc2u.server.features.expense.specification.ExpenseSpecification;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.user.entity.User;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Trava o N+1 nas consultas de despesa que alimentam o ExpenseResponse (descrição, cartão e categoria). O cenário tem
 * vários cartões e várias categorias: com um único cartão/categoria o N+1 passaria despercebido. A contagem usa
 * getPrepareStatementCount() (SQL preparado, inclusive carregamentos preguiçosos), não getQueryExecutionCount().
 *
 * O total esperado das listagens é 1: a consulta de despesas, com cartão e categoria no @EntityGraph. O dono (Expense.user e
 * CardAccount.user) é LAZY e nenhum Response o lê, então não há consulta extra. Antes do LAZY o total era 2 (um custo
 * fixo do dono EAGER). O que o teste protege é o crescimento: shouldNotGrowWithTheNumberOfCards.
 */
@DataJpaTest
class ExpenseRepositoryTest {

    private static final int CARDS = 3;
    private static final int EXPENSES_PER_CARD = 2;
    private static final int LIST_STATEMENTS = 1;
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final LocalDate OCTOBER = LocalDate.of(2026, 10, 1);
    private static final LocalDate NOVEMBER = LocalDate.of(2026, 11, 1);

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
    void findByUserIdAndDueDateBetween_shouldLoadCardAndCategoryInTheMainQuery() {
        var expenses = expenseRepository.findByUserIdAndDueDateBetween(
            user.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertEquals(CARDS * EXPENSES_PER_CARD, expenses.size());
        expenses.forEach(expense -> {
            assertNotNull(expense.getCardAccount().getCardName());
            assertNotNull(expense.getCategory().getName());
        });
        assertEquals(LIST_STATEMENTS, statistics.getPrepareStatementCount());
    }

    // O resumo soma as despesas do mês: o primeiro e o último dia contam, os vizinhos e as de outro usuário não.
    @Test
    void findByUserIdAndDueDateBetween_shouldIncludeTheFirstAndTheLastDay_andExcludeTheNeighboringDays() {
        var cardAccount = persistCard("Cartão do período");
        var category = persistCategory("Categoria do período");
        persistExpense("Dia anterior", cardAccount, category, PaymentStatus.PENDING, LocalDate.of(2026, 6, 30));
        persistExpense("Primeiro dia", cardAccount, category, PaymentStatus.PENDING, LocalDate.of(2026, 7, 1));
        persistExpense("Último dia", cardAccount, category, PaymentStatus.PENDING, LocalDate.of(2026, 7, 31));
        persistExpense("Dia seguinte", cardAccount, category, PaymentStatus.PENDING, LocalDate.of(2026, 8, 1));
        var other = testEntityManager.persist(User.builder().name("Bia").email("bia.periodo@email.com")
            .savingsBalance(BigDecimal.ZERO).build());
        var otherCategory = testEntityManager.persist(Category.builder().name("Outra").user(other).build());
        persistParceled("Do outro usuário", null, otherCategory, PaymentStatus.PENDING, LocalDate.of(2026, 7, 15), other);
        resetPersistenceContext();

        var found = expenseRepository.findByUserIdAndDueDateBetween(
            user.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertEquals(List.of("Primeiro dia", "Último dia"),
            found.stream().sorted(Comparator.comparing(Expense::getDueDate)).map(Expense::getDescription).toList());
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

    @Test
    void existsByRecurringExpenseIdAndRecurrenceMonth_shouldMatchOnlyTheSameModelAndMonth_whateverTheDueDate() {
        var model = persistRecurringExpense("Spotify");
        var otherModel = persistRecurringExpense("Netflix");
        testEntityManager.persistAndFlush(newInstance(model, OCTOBER, LocalDate.of(2026, 11, 5)));

        assertTrue(expenseRepository.existsByRecurringExpenseIdAndRecurrenceMonth(model.getId(), OCTOBER));
        assertFalse(expenseRepository.existsByRecurringExpenseIdAndRecurrenceMonth(model.getId(), NOVEMBER),
            "o vencimento cai em novembro, mas o mês de geração é outubro");
        assertFalse(expenseRepository.existsByRecurringExpenseIdAndRecurrenceMonth(otherModel.getId(), OCTOBER));
    }

    // Trava a premissa da idempotência: o banco recusa a segunda despesa do mesmo modelo no mesmo mês, mesmo com outro vencimento.
    @Test
    void save_shouldFailWithUniqueViolation_whenTheModelAlreadyHasAnExpenseInTheMonth_evenWithAnotherDueDate() {
        var model = persistRecurringExpense("Spotify");
        persistInstance(model, LocalDate.of(2026, 10, 5));

        var exception = assertThrows(DataIntegrityViolationException.class,
            () -> expenseRepository.saveAndFlush(newInstance(model, OCTOBER, LocalDate.of(2026, 10, 20))));

        ConstraintViolationException violation = null;
        for (Throwable cause = exception; cause != null && violation == null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                violation = constraintViolation;
            }
        }

        assertNotNull(violation, "A causa deveria conter ConstraintViolationException.");
        assertEquals(ConstraintViolationException.ConstraintKind.UNIQUE, violation.getKind());
    }

    @Test
    void save_shouldAcceptTheSameMonth_forAnotherModelAndForExpensesWithoutModel_andAnotherMonthForTheSameModel() {
        var model = persistRecurringExpense("Spotify");
        var otherModel = persistRecurringExpense("Netflix");
        var dueDate = LocalDate.of(2026, 10, 5);
        persistInstance(model, dueDate);

        assertDoesNotThrow(() -> {
            expenseRepository.saveAndFlush(newInstance(model, LocalDate.of(2026, 11, 5)));
            expenseRepository.saveAndFlush(newInstance(otherModel, dueDate));
            expenseRepository.saveAndFlush(newInstance(null, dueDate));
            expenseRepository.saveAndFlush(newInstance(null, dueDate));
        });
    }

    @Test
    void detachFromRecurringExpense_shouldKeepTheExpensesOfTheModelWithoutTheLinkAndTheMonth_andLeaveTheOthersAlone() {
        var model = persistRecurringExpense("Spotify");
        var otherModel = persistRecurringExpense("Netflix");
        var october = persistInstance(model, LocalDate.of(2026, 10, 5));
        var november = persistInstance(model, LocalDate.of(2026, 11, 5));
        var other = persistInstance(otherModel, LocalDate.of(2026, 10, 6));
        resetPersistenceContext();

        expenseRepository.detachFromRecurringExpense(model.getId());
        resetPersistenceContext();

        assertNull(expenseRepository.findById(october.getId()).orElseThrow().getRecurringExpenseId());
        assertNull(expenseRepository.findById(october.getId()).orElseThrow().getRecurrenceMonth());
        assertNull(expenseRepository.findById(november.getId()).orElseThrow().getRecurringExpenseId());
        assertNull(expenseRepository.findById(november.getId()).orElseThrow().getRecurrenceMonth());
        var untouched = expenseRepository.findById(other.getId()).orElseThrow();
        assertEquals(otherModel.getId(), untouched.getRecurringExpenseId());
        assertEquals(OCTOBER, untouched.getRecurrenceMonth());
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

    private void persistParceled(String description, CardAccount cardAccount, Category category,
                                 PaymentStatus paymentStatus, LocalDate dueDate, User owner) {
        persistParceled(description, cardAccount, category, paymentStatus, dueDate, owner, UUID.randomUUID(), 2);
    }

    private void persistParceled(String description, CardAccount cardAccount, Category category,
                                 PaymentStatus paymentStatus, LocalDate dueDate, User owner, UUID groupId,
                                 int currentInstallment) {
        testEntityManager.persist(Expense.builder()
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

    private RecurringExpense persistRecurringExpense(String description) {
        var model = RecurringExpense.of(description, BigDecimal.TEN, 5, YearMonth.of(2026, 10), null);
        model.setUser(user);
        model.setCategory(persistCategory("Categoria " + description));
        return testEntityManager.persist(model);
    }

    private Expense newInstance(RecurringExpense model, LocalDate dueDate) {
        return newInstance(model, YearMonth.from(dueDate).atDay(1), dueDate);
    }

    private Expense newInstance(RecurringExpense model, LocalDate recurrenceMonth, LocalDate dueDate) {
        return Expense.builder()
            .description("Instância")
            .value(BigDecimal.TEN)
            .dueDate(dueDate)
            .expenseType(ExpenseType.FIXED)
            .paymentStatus(PaymentStatus.PENDING)
            .user(user)
            .category(persistCategory("Categoria da instância " + UUID.randomUUID()))
            .recurringExpenseId(model != null
                ? model.getId()
                : null)
            .recurrenceMonth(model != null
                ? recurrenceMonth
                : null)
            .build();
    }

    private Expense persistInstance(RecurringExpense model, LocalDate dueDate) {
        return testEntityManager.persistAndFlush(newInstance(model, dueDate));
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
