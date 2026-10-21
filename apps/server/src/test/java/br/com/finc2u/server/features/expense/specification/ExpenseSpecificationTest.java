package br.com.finc2u.server.features.expense.specification;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.filter.ExpenseFilter;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import br.com.finc2u.server.features.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * Confere o RESULTADO de cada critério do filtro (e da combinação) contra o banco, não só a contagem de queries. O
 * cenário é pequeno e as datas ficam nas bordas (primeiro e último dia do mês e do ano), onde os filtros costumam errar.
 *
 * Despesas da Ana (valor entre parênteses): Aluguel 01/06 FIXED PENDING Nubank Moradia (1000) · Mercado 30/06 VARIABLE
 * PAID Nubank Moradia (200) · Cinema 31/05 VARIABLE PAID Mercado Pago Lazer (50) · Show 01/07 VARIABLE PENDING Mercado
 * Pago Lazer (300) · Notebook 15/06 PARCELED PENDING Nubank Lazer (500) · Seguro 31/12/2025 FIXED PAID avulsa Moradia
 * (120) · Curso 31/12/2026 FIXED PENDING avulsa Lazer (75). Mais uma despesa do Beto, que nunca pode aparecer.
 */
@DataJpaTest
class ExpenseSpecificationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private User ana;
    private CardAccount nubank;
    private CardAccount mercadoPago;
    private Category housing;
    private Category leisure;

    @BeforeEach
    void setUp() {
        ana = persistUser("Ana", "ana@email.com");
        var beto = persistUser("Beto", "beto@email.com");
        nubank = persistCard("Nubank", ana);
        mercadoPago = persistCard("Mercado Pago", ana);
        housing = persistCategory("Moradia", ana);
        leisure = persistCategory("Lazer", ana);

        persist("Aluguel", "1000", LocalDate.of(2026, 6, 1), PaymentStatus.PENDING, ExpenseType.FIXED, nubank, housing);
        persist("Mercado", "200", LocalDate.of(2026, 6, 30), PaymentStatus.PAID, ExpenseType.VARIABLE, nubank, housing);
        persist("Cinema", "50", LocalDate.of(2026, 5, 31), PaymentStatus.PAID, ExpenseType.VARIABLE, mercadoPago, leisure);
        persist("Show", "300", LocalDate.of(2026, 7, 1), PaymentStatus.PENDING, ExpenseType.VARIABLE, mercadoPago, leisure);
        persist("Notebook", "500", LocalDate.of(2026, 6, 15), PaymentStatus.PENDING, ExpenseType.PARCELED, nubank, leisure);
        persist("Seguro", "120", LocalDate.of(2025, 12, 31), PaymentStatus.PAID, ExpenseType.FIXED, null, housing);
        persist("Curso", "75", LocalDate.of(2026, 12, 31), PaymentStatus.PENDING, ExpenseType.FIXED, null, leisure);

        var betoCategory = persistCategory("Moradia", beto);
        testEntityManager.persist(Expense.builder()
            .description("Alheia")
            .value(new BigDecimal("1"))
            .dueDate(LocalDate.of(2026, 6, 15))
            .expenseType(ExpenseType.VARIABLE)
            .paymentStatus(PaymentStatus.PAID)
            .user(beto)
            .category(betoCategory)
            .build());
        testEntityManager.flush();
        testEntityManager.clear();
    }

    @Test
    void shouldReturnAllTheUsersExpenses_andNeverTheOthers_whenNoCriterionIsInformed() {
        assertEquals(List.of("Aluguel", "Cinema", "Curso", "Mercado", "Notebook", "Seguro", "Show"),
            matching(new Criteria()));
    }

    @Test
    void shouldIncludeTheFirstAndTheLastDayOfTheMonth_andExcludeTheNeighboringDays() {
        assertEquals(List.of("Aluguel", "Mercado", "Notebook"), matching(new Criteria().month(6).year(2026)));
        assertEquals(List.of("Show"), matching(new Criteria().month(7).year(2026)));
        assertEquals(List.of("Cinema"), matching(new Criteria().month(5).year(2026)));
    }

    @Test
    void shouldRespectTheYearBoundary_inTheLastDayOfDecember() {
        assertEquals(List.of("Seguro"), matching(new Criteria().month(12).year(2025)));
        assertEquals(List.of("Curso"), matching(new Criteria().month(12).year(2026)));
    }

    @Test
    void shouldCoverTheWholeYear_whenOnlyTheYearIsInformed() {
        assertEquals(List.of("Aluguel", "Cinema", "Curso", "Mercado", "Notebook", "Show"),
            matching(new Criteria().year(2026)));
    }

    @Test
    void shouldFilterByStatus() {
        assertEquals(List.of("Cinema", "Mercado", "Seguro"), matching(new Criteria().status(PaymentStatus.PAID)));
        assertEquals(List.of("Aluguel", "Curso", "Notebook", "Show"), matching(new Criteria().status(PaymentStatus.PENDING)));
    }

    @Test
    void shouldFilterByType() {
        assertEquals(List.of("Aluguel", "Curso", "Seguro"), matching(new Criteria().type(ExpenseType.FIXED)));
        assertEquals(List.of("Cinema", "Mercado", "Show"), matching(new Criteria().type(ExpenseType.VARIABLE)));
        assertEquals(List.of("Notebook"), matching(new Criteria().type(ExpenseType.PARCELED)));
    }

    @Test
    void shouldFilterByCard_andLeaveOutTheExpensesWithoutCard() {
        assertEquals(List.of("Aluguel", "Mercado", "Notebook"), matching(new Criteria().card(nubank.getId())));
        assertEquals(List.of("Cinema", "Show"), matching(new Criteria().card(mercadoPago.getId())));
    }

    @Test
    void shouldFilterByCategory() {
        assertEquals(List.of("Aluguel", "Mercado", "Seguro"), matching(new Criteria().category(housing.getId())));
        assertEquals(List.of("Cinema", "Curso", "Notebook", "Show"), matching(new Criteria().category(leisure.getId())));
    }

    @Test
    void shouldTreatTheMinimumPriceAsInclusive() {
        assertEquals(List.of("Aluguel", "Notebook", "Show"), matching(new Criteria().minPrice("300")));
    }

    @Test
    void shouldTreatTheMaximumPriceAsInclusive() {
        assertEquals(List.of("Cinema", "Curso", "Mercado", "Seguro"), matching(new Criteria().maxPrice("200")));
    }

    @Test
    void shouldFilterByPriceRange_withBothLimitsInclusive() {
        assertEquals(List.of("Mercado", "Seguro", "Show"), matching(new Criteria().minPrice("120").maxPrice("300")));
    }

    @Test
    void shouldCombineEveryCriterion() {
        assertEquals(List.of("Notebook"), matching(new Criteria().month(6).year(2026).status(PaymentStatus.PENDING)
            .type(ExpenseType.PARCELED).card(nubank.getId()).category(leisure.getId()).minPrice("500").maxPrice("500")));
        assertEquals(List.of("Cinema"), matching(new Criteria().year(2026).type(ExpenseType.VARIABLE)
            .status(PaymentStatus.PAID).maxPrice("100")));
    }

    @Test
    void shouldReturnNothing_whenTheCriteriaContradictEachOther() {
        assertEquals(List.of(), matching(new Criteria().month(6).year(2026).card(mercadoPago.getId())
            .type(ExpenseType.FIXED)));
    }

    private List<String> matching(Criteria criteria) {
        return expenseRepository.findAll(ExpenseSpecification.ofFilter(ana.getId(), criteria.build(), TODAY)).stream()
            .map(Expense::getDescription)
            .sorted()
            .toList();
    }

    private void persist(String description, String value, LocalDate dueDate, PaymentStatus status, ExpenseType type,
                         CardAccount card, Category category) {
        testEntityManager.persist(Expense.builder()
            .description(description)
            .value(new BigDecimal(value))
            .dueDate(dueDate)
            .expenseType(type)
            .paymentStatus(status)
            .user(ana)
            .cardAccount(card)
            .category(category)
            .build());
    }

    private User persistUser(String name, String email) {
        return testEntityManager.persist(User.builder()
            .name(name)
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build());
    }

    private CardAccount persistCard(String name, User owner) {
        return testEntityManager.persist(CardAccount.builder()
            .cardName(name)
            .closingDate(10)
            .dueDate(17)
            .user(owner)
            .build());
    }

    private Category persistCategory(String name, User owner) {
        return testEntityManager.persist(Category.builder()
            .name(name)
            .user(owner)
            .build());
    }

    // Monta o ExpenseFilter só com os critérios do teste (os demais ficam nulos = sem restrição).
    private static class Criteria {

        private Integer month;
        private Integer year;
        private PaymentStatus status;
        private ExpenseType type;
        private UUID card;
        private UUID category;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;

        Criteria month(int month) {
            this.month = month;
            return this;
        }

        Criteria year(int year) {
            this.year = year;
            return this;
        }

        Criteria status(PaymentStatus status) {
            this.status = status;
            return this;
        }

        Criteria type(ExpenseType type) {
            this.type = type;
            return this;
        }

        Criteria card(UUID card) {
            this.card = card;
            return this;
        }

        Criteria category(UUID category) {
            this.category = category;
            return this;
        }

        Criteria minPrice(String minPrice) {
            this.minPrice = new BigDecimal(minPrice);
            return this;
        }

        Criteria maxPrice(String maxPrice) {
            this.maxPrice = new BigDecimal(maxPrice);
            return this;
        }

        ExpenseFilter build() {
            return new ExpenseFilter(month, year, status, type, card, category, minPrice, maxPrice, null);
        }

    }

}
