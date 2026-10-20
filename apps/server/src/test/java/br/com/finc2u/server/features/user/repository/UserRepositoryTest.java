package br.com.finc2u.server.features.user.repository;

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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        persistUser("Ana", "ana@email.com");
        persistUser("Beto", "beto@email.com");
        persistUser("Carla", "carla@email.com");
        testEntityManager.flush();
        testEntityManager.clear();

        statistics = entityManager.getEntityManagerFactory()
            .unwrap(SessionFactory.class)
            .getStatistics();
        statistics.clear();
    }

    @Test
    void findAll_shouldLoadUsersInSingleQuery() {
        List<User> users = userRepository.findAll();

        assertEquals(3, users.size());
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    @Test
    void findByEmail_shouldLoadUserInSingleQuery() {
        User user = userRepository.findByEmail("beto@email.com")
            .orElseThrow();

        assertEquals(0, BigDecimal.valueOf(100).compareTo(user.getSavingsBalance()));
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    // Trava a premissa do GlobalExceptionHandler: e-mail duplicado chega como violação do tipo UNIQUE.
    @Test
    void save_shouldFailWithUniqueViolation_whenEmailIsDuplicated() {
        var duplicated = User.builder()
            .name("Outra Ana")
            .email("ana@email.com")
            .build();

        var exception = assertThrows(DataIntegrityViolationException.class,
            () -> userRepository.saveAndFlush(duplicated));

        ConstraintViolationException violation = null;
        for (Throwable cause = exception; cause != null && violation == null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                violation = constraintViolation;
            }
        }

        assertNotNull(violation, "A causa deveria conter ConstraintViolationException.");
        assertEquals(ConstraintViolationException.ConstraintKind.UNIQUE, violation.getKind());
    }

    private void persistUser(String name, String email) {
        User user = User.builder()
            .name(name)
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build();
        testEntityManager.persist(user);
    }

}
