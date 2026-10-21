package br.com.finc2u.server.features.summary.repository;

import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.entity.MonthlySummaryCategoryTotal;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/*
 * O histórico lê os totais por categoria de vários resumos: o N+1 aqui seria uma consulta por resumo. O cenário tem vários
 * resumos e várias categorias, e a contagem usa getPrepareStatementCount() (inclui carregamentos preguiçosos).
 */
@DataJpaTest
class MonthlySummaryCategoryTotalRepositoryTest {

    @Autowired
    private MonthlySummaryCategoryTotalRepository categoryTotalRepository;

    @Autowired
    private MonthlySummaryRepository monthlySummaryRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;
    private User ana;
    private Category groceries;
    private Category leisure;
    private List<MonthlySummary> summaries;

    @BeforeEach
    void setUp() {
        ana = testEntityManager.persist(User.builder()
            .name("Ana")
            .email("ana@email.com")
            .savingsBalance(BigDecimal.valueOf(100))
            .build());
        groceries = persistCategory("Alimentação");
        leisure = persistCategory("Lazer");
        var transport = persistCategory("Transporte");

        summaries = new ArrayList<>();
        for (var month = 1; month <= 3; month++) {
            var summary = testEntityManager.persist(MonthlySummary.forPeriod(ana, month, 2026));
            summaries.add(summary);
            persistTotal(summary, groceries, 100 * month);
            persistTotal(summary, leisure, 10 * month);
            persistTotal(summary, transport, month);
        }
        resetStatistics();
    }

    @Test
    void findBySummaryIdIn_shouldServeAllTheSummariesInASingleStatement_readingTheCategoryName() {
        var totals = categoryTotalRepository.findBySummaryIdInOrderByCategoryNameAsc(idsOf(summaries));

        assertEquals(9, totals.size());
        totals.forEach(total -> assertNotNull(total.getCategory().getName()));
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    @Test
    void findBySummaryIdIn_shouldOrderTheRowsByCategoryName() {
        var totals = categoryTotalRepository.findBySummaryIdInOrderByCategoryNameAsc(idsOf(summaries));

        var names = totals.stream().map(total -> total.getCategory().getName()).toList();
        assertEquals(names.stream().sorted().toList(), names);
        assertEquals("Alimentação", names.getFirst());
        assertEquals("Transporte", names.getLast());
    }

    @Test
    void findBySummaryIdIn_shouldReturnOnlyTheRequestedSummaries() {
        var totals = categoryTotalRepository.findBySummaryIdInOrderByCategoryNameAsc(List.of(summaries.getFirst().getId()));

        assertEquals(3, totals.size());
        assertTrue(totals.stream().allMatch(total -> total.getSummary().getId().equals(summaries.getFirst().getId())));
    }

    @Test
    void findBySummaryIdIn_shouldReturnNothing_forUnknownIds() {
        assertTrue(categoryTotalRepository.findBySummaryIdInOrderByCategoryNameAsc(List.of(UUID.randomUUID())).isEmpty());
    }

    // O que o service faz no histórico: os resumos (1 consulta) e os totais de todos eles (1 consulta), e só.
    @Test
    void history_shouldCostTwoStatements_andNotGrowWithTheNumberOfSummaries() {
        readHistory();
        var statementsWithFewSummaries = statistics.getPrepareStatementCount();

        for (var month = 4; month <= 12; month++) {
            var summary = testEntityManager.persist(MonthlySummary.forPeriod(ana, month, 2026));
            persistTotal(summary, groceries, month);
            persistTotal(summary, leisure, month);
        }
        resetStatistics();
        readHistory();

        assertEquals(2, statementsWithFewSummaries);
        assertEquals(statementsWithFewSummaries, statistics.getPrepareStatementCount(),
            "Mais resumos não podem gerar mais statements (N+1)");
    }

    @Test
    void deleteBySummaryId_shouldRemoveOnlyTheRowsOfThatSummary() {
        var target = summaries.get(1);

        categoryTotalRepository.deleteBySummaryId(target.getId());
        resetStatistics();

        assertTrue(categoryTotalRepository.findBySummaryIdInOrderByCategoryNameAsc(List.of(target.getId())).isEmpty());
        assertEquals(6, categoryTotalRepository.findBySummaryIdInOrderByCategoryNameAsc(
            List.of(summaries.get(0).getId(), summaries.get(2).getId())).size());
    }

    // Trava a premissa do recálculo: sem apagar os antigos, a mesma categoria no mesmo resumo violaria a unique.
    @Test
    void save_shouldFailWithUniqueViolation_whenTheCategoryIsRepeatedInTheSameSummary() {
        var exception = assertThrows(DataIntegrityViolationException.class,
            () -> categoryTotalRepository.saveAndFlush(
                MonthlySummaryCategoryTotal.of(summaries.getFirst(), groceries, BigDecimal.TEN)));

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
    void save_shouldAcceptTheSameCategory_inAnotherSummary() {
        var other = testEntityManager.persist(MonthlySummary.forPeriod(ana, 12, 2026));

        assertDoesNotThrow(() -> categoryTotalRepository.saveAndFlush(
            MonthlySummaryCategoryTotal.of(other, groceries, BigDecimal.TEN)));
    }

    private void readHistory() {
        var history = monthlySummaryRepository.findByUserId(ana.getId());
        categoryTotalRepository.findBySummaryIdInOrderByCategoryNameAsc(idsOf(history))
            .forEach(total -> assertNotNull(total.getCategory().getName()));
    }

    private List<UUID> idsOf(List<MonthlySummary> list) {
        return list.stream().map(MonthlySummary::getId).toList();
    }

    private Category persistCategory(String name) {
        return testEntityManager.persist(Category.builder()
            .name(name)
            .user(ana)
            .build());
    }

    private void persistTotal(MonthlySummary summary, Category category, int total) {
        testEntityManager.persist(MonthlySummaryCategoryTotal.of(summary, category, BigDecimal.valueOf(total)));
    }

    private void resetStatistics() {
        testEntityManager.flush();
        testEntityManager.clear();
        statistics = entityManager.getEntityManagerFactory()
            .unwrap(SessionFactory.class)
            .getStatistics();
        statistics.clear();
    }

}
