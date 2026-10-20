package br.com.finc2u.server.features.salaryhistory.repository;

import br.com.finc2u.server.features.salaryhistory.entity.SalaryHistory;
import br.com.finc2u.server.features.salaryhistory.vo.CurrentSalary;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class SalaryHistoryRepositoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    @Autowired
    private SalaryHistoryRepository salaryHistoryRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;
    private User ana;
    private User beto;
    private User carla;

    @BeforeEach
    void setUp() {
        ana = persistUser("Ana", "ana@email.com");
        beto = persistUser("Beto", "beto@email.com");
        carla = persistUser("Carla", "carla@email.com");

        persistSalary(ana, 3000, LocalDate.of(2026, 1, 1));
        persistSalary(ana, 3500, LocalDate.of(2026, 8, 1));
        persistSalary(ana, 9999, LocalDate.of(2027, 1, 1));
        persistSalary(beto, 4200, LocalDate.of(2026, 3, 1));
        persistSalary(carla, 5000, LocalDate.of(2027, 2, 1));

        testEntityManager.flush();
        testEntityManager.clear();

        statistics = entityManager.getEntityManagerFactory()
            .unwrap(SessionFactory.class)
            .getStatistics();
        statistics.clear();
    }

    @Test
    void findCurrentSalaries_shouldReturnTheMostRecentStartedSalaryOfEachUser() {
        var result = bySalaryUser(salaryHistoryRepository.findCurrentSalaries(
            List.of(ana.getId(), beto.getId(), carla.getId()), TODAY));

        assertEquals(2, result.size());
        assertEquals(0, BigDecimal.valueOf(3500).compareTo(result.get(ana.getId()).baseSalary()));
        assertEquals(0, BigDecimal.valueOf(4200).compareTo(result.get(beto.getId()).baseSalary()));
    }

    @Test
    void findCurrentSalaries_shouldIgnoreFutureSalaries() {
        var result = bySalaryUser(salaryHistoryRepository.findCurrentSalaries(List.of(carla.getId()), TODAY));

        assertFalse(result.containsKey(carla.getId()));
    }

    @Test
    void findCurrentSalaries_shouldAcceptASalaryStartingExactlyOnTheDate() {
        var result = bySalaryUser(salaryHistoryRepository.findCurrentSalaries(
            List.of(ana.getId()), LocalDate.of(2026, 8, 1)));

        assertEquals(0, BigDecimal.valueOf(3500).compareTo(result.get(ana.getId()).baseSalary()));
    }

    @Test
    void findCurrentSalaries_shouldOnlyReturnTheRequestedUsers() {
        var result = salaryHistoryRepository.findCurrentSalaries(List.of(beto.getId()), TODAY);

        assertEquals(1, result.size());
        assertTrue(result.stream().allMatch(salary -> salary.userId().equals(beto.getId())));
    }

    @Test
    void findCurrentSalaries_shouldServeThreeUsersInSingleQuery() {
        salaryHistoryRepository.findCurrentSalaries(List.of(ana.getId(), beto.getId(), carla.getId()), TODAY);

        assertEquals(1, statistics.getPrepareStatementCount());
    }

    private Map<UUID, CurrentSalary> bySalaryUser(List<CurrentSalary> salaries) {
        return salaries.stream()
            .collect(Collectors.toMap(CurrentSalary::userId, Function.identity()));
    }

    private User persistUser(String name, String email) {
        var user = User.builder()
            .name(name)
            .email(email)
            .build();
        user.setUserConfiguration(UserConfiguration.builder()
            .savingsBalance(BigDecimal.valueOf(100))
            .build());
        return testEntityManager.persist(user);
    }

    private void persistSalary(User user, int baseSalary, LocalDate effectiveFrom) {
        testEntityManager.persist(SalaryHistory.builder()
            .user(user)
            .baseSalary(BigDecimal.valueOf(baseSalary))
            .effectiveFrom(effectiveFrom)
            .build());
    }

}
