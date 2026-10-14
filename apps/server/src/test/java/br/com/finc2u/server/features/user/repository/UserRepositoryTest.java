package br.com.finc2u.server.features.user.repository;

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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
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
    void findAll_shouldLoadConfigurationInSingleQuery() {
        List<User> users = userRepository.findAll();

        assertEquals(3, users.size());
        users.forEach(user -> assertNotNull(user.getUserConfiguration().getBaseSalary()));
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    @Test
    void findByEmail_shouldLoadConfigurationInSingleQuery() {
        User user = userRepository.findByEmail("beto@email.com")
                .orElseThrow();

        assertEquals(0, BigDecimal.valueOf(3000).compareTo(user.getUserConfiguration().getBaseSalary()));
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    private void persistUser(String name, String email) {
        User user = User.builder()
                .name(name)
                .email(email)
                .build();
        user.setUserConfiguration(UserConfiguration.builder()
                .baseSalary(BigDecimal.valueOf(3000))
                .savingsBalance(BigDecimal.valueOf(100))
                .build());
        testEntityManager.persist(user);
    }

}
