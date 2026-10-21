package br.com.finc2u.server.features.extraincome.repository;

import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import br.com.finc2u.server.features.extraincome.form.ExtraIncomeResponse;
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
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class ExtraIncomeRepositoryTest {

    @Autowired
    private ExtraIncomeRepository extraIncomeRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private EntityManager entityManager;

    private User ana;

    @BeforeEach
    void setUp() {
        ana = persistUser("Ana", "ana@email.com");
        var beto = persistUser("Beto", "beto@email.com");

        persistExtraIncome("Dia anterior", ana, LocalDate.of(2026, 5, 31));
        persistExtraIncome("Primeiro dia", ana, LocalDate.of(2026, 6, 1));
        persistExtraIncome("Meio do mês", ana, LocalDate.of(2026, 6, 15));
        persistExtraIncome("Último dia", ana, LocalDate.of(2026, 6, 30));
        persistExtraIncome("Dia seguinte", ana, LocalDate.of(2026, 7, 1));
        persistExtraIncome("Do Beto", beto, LocalDate.of(2026, 6, 15));
        testEntityManager.flush();
        testEntityManager.clear();
    }

    // O resumo soma os ganhos do mês: o primeiro e o último dia contam, os vizinhos não.
    @Test
    void findByUserIdAndDateBetween_shouldIncludeTheFirstAndTheLastDay_andExcludeTheNeighboringDays() {
        var found = extraIncomeRepository.findByUserIdAndDateBetween(
            ana.getId(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertEquals(List.of("Primeiro dia", "Meio do mês", "Último dia"),
            found.stream().sorted(Comparator.comparing(ExtraIncome::getDate)).map(ExtraIncome::getDescription).toList());
    }

    @Test
    void findByUserIdAndDateBetween_shouldNeverReturnTheExtraIncomesOfAnotherUser() {
        var found = extraIncomeRepository.findByUserIdAndDateBetween(
            ana.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertEquals(5, found.size());
        assertTrue(found.stream().noneMatch(extraIncome -> "Do Beto".equals(extraIncome.getDescription())));
    }

    @Test
    void findByUserIdAndDateBetween_shouldReturnTheSingleDay_whenStartEqualsEnd() {
        var found = extraIncomeRepository.findByUserIdAndDateBetween(
            ana.getId(), LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15));

        assertEquals(List.of("Meio do mês"), found.stream().map(ExtraIncome::getDescription).toList());
    }

    @Test
    void findByUserIdAndDateBetween_shouldReturnNothing_whenThereIsNoExtraIncomeInThePeriod() {
        var found = extraIncomeRepository.findByUserIdAndDateBetween(
            ana.getId(), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31));

        assertTrue(found.isEmpty());
    }

    // O ExtraIncomeResponse lê só o id do dono (LAZY): montar a resposta não custa nenhuma consulta a mais.
    @Test
    void findByUserId_shouldServeTheListingInASingleStatement_evenReadingTheResponse() {
        var statistics = resetStatistics();

        var responses = extraIncomeRepository.findByUserId(ana.getId()).stream()
            .map(ExtraIncomeResponse::from)
            .toList();

        assertEquals(5, responses.size());
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    @Test
    void findByUserIdAndDateBetween_shouldServeThePeriodInASingleStatement_evenReadingTheResponse() {
        var statistics = resetStatistics();

        var responses = extraIncomeRepository.findByUserIdAndDateBetween(
                ana.getId(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)).stream()
            .map(ExtraIncomeResponse::from)
            .toList();

        assertEquals(3, responses.size());
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    private Statistics resetStatistics() {
        testEntityManager.flush();
        testEntityManager.clear();
        var statistics = entityManager.getEntityManagerFactory()
            .unwrap(SessionFactory.class)
            .getStatistics();
        statistics.clear();
        return statistics;
    }

    private User persistUser(String name, String email) {
        return testEntityManager.persist(User.builder()
            .name(name)
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build());
    }

    private void persistExtraIncome(String description, User user, LocalDate date) {
        testEntityManager.persist(ExtraIncome.builder()
            .description(description)
            .value(BigDecimal.TEN)
            .date(date)
            .user(user)
            .build());
    }

}
