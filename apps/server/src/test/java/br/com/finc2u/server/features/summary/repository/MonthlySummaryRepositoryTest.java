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

    private User persistUser(String name, String email) {
        return testEntityManager.persist(User.builder()
            .name(name)
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build());
    }

}
