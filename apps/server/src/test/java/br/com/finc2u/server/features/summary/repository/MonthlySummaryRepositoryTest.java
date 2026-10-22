package br.com.finc2u.server.features.summary.repository;

import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.form.MonthlySummaryResponse;
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
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class MonthlySummaryRepositoryTest {

    @Autowired
    private MonthlySummaryRepository monthlySummaryRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;
    private User ana;

    @BeforeEach
    void setUp() {
        ana = persistUser("Ana", "ana@email.com");
        var beto = persistUser("Beto", "beto@email.com");
        for (var month = 1; month <= 3; month++) {
            testEntityManager.persist(MonthlySummary.forPeriod(ana, month, 2026));
        }
        testEntityManager.persist(MonthlySummary.forPeriod(beto, 1, 2026));

        testEntityManager.flush();
        testEntityManager.clear();
        statistics = entityManager.getEntityManagerFactory()
            .unwrap(SessionFactory.class)
            .getStatistics();
        statistics.clear();
    }

    // O MonthlySummaryResponse lê só o id do dono (LAZY): o histórico sai numa consulta, com a resposta montada.
    @Test
    void findByUserId_shouldServeTheHistoryInASingleStatement_evenReadingTheResponse() {
        var responses = monthlySummaryRepository.findByUserId(ana.getId()).stream()
            .map(summary -> MonthlySummaryResponse.from(summary, List.of()))
            .toList();

        assertEquals(3, responses.size());
        assertTrue(responses.stream().allMatch(response -> ana.getId().equals(response.userId())));
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    @Test
    void findByUserIdAndMonthAndYear_shouldServeThePeriodInASingleStatement_evenReadingTheResponse() {
        var summary = monthlySummaryRepository.findByUserIdAndMonthAndYear(ana.getId(), 2, 2026).orElseThrow();

        assertEquals(ana.getId(), MonthlySummaryResponse.from(summary, List.of()).userId());
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    @Test
    void findByUserIdAndMonthAndYear_shouldNotFindThePeriodOfAnotherUser() {
        assertTrue(monthlySummaryRepository.findByUserIdAndMonthAndYear(ana.getId(), 4, 2026).isEmpty());
    }

    // ---- findFromPeriod: o alcance e a ordem da cascata do carryover ----

    // Do mês informado em diante (inclusive), do mais antigo ao mais novo, atravessando a virada do ano e pulando as lacunas.
    @Test
    void findFromPeriod_shouldReturnTheExistingSummariesFromThePeriodOn_inAscendingOrder() {
        persistAndReset(ana, 1, 2027);
        persistAndReset(ana, 12, 2026);

        var found = monthlySummaryRepository.findFromPeriod(ana.getId(), 2026, 2);

        assertEquals(List.of("2026-02", "2026-03", "2026-12", "2027-01"), found.stream()
            .map(summary -> YearMonth.of(summary.getYear(), summary.getMonth()).toString())
            .toList());
    }

    @Test
    void findFromPeriod_shouldNotReturnTheSummariesOfAnotherUser() {
        var found = monthlySummaryRepository.findFromPeriod(ana.getId(), 2026, 1);

        assertEquals(3, found.size());
        assertTrue(found.stream().allMatch(summary -> ana.getId().equals(summary.getUser().getId())));
    }

    @Test
    void findFromPeriod_shouldReturnNothing_whenThereIsNoSummaryFromThePeriodOn() {
        assertTrue(monthlySummaryRepository.findFromPeriod(ana.getId(), 2026, 4).isEmpty());
    }

    @Test
    void findFromPeriod_shouldServeTheCascadeInASingleStatement_evenReadingTheResponse() {
        var responses = monthlySummaryRepository.findFromPeriod(ana.getId(), 2026, 1).stream()
            .map(summary -> MonthlySummaryResponse.from(summary, List.of()))
            .toList();

        assertEquals(3, responses.size());
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    private void persistAndReset(User user, int month, int year) {
        testEntityManager.persist(MonthlySummary.forPeriod(user, month, year));
        testEntityManager.flush();
        testEntityManager.clear();
        statistics.clear();
    }

    private User persistUser(String name, String email) {
        return testEntityManager.persist(User.builder()
            .name(name)
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build());
    }

}
