package br.com.finc2u.server.features.category.repository;

import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.user.entity.User;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private User ana;
    private User beto;

    @BeforeEach
    void setUp() {
        ana = persistUser("Ana", "ana@email.com");
        beto = persistUser("Beto", "beto@email.com");
    }

    // Trava a premissa do GlobalExceptionHandler: nome duplicado no mesmo usuário chega como violação do tipo UNIQUE.
    @Test
    void save_shouldFailWithUniqueViolation_whenNameIsDuplicatedForTheSameUser() {
        persistCategory("Alimentação", ana);

        var exception = assertThrows(DataIntegrityViolationException.class,
            () -> categoryRepository.saveAndFlush(newCategory("Alimentação", ana)));

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
    void save_shouldAcceptTheSameName_forAnotherUser() {
        persistCategory("Alimentação", ana);

        assertDoesNotThrow(() -> categoryRepository.saveAndFlush(newCategory("Alimentação", beto)));
    }

    @Test
    void findByUserId_shouldReturnOnlyCategoriesOfTheUser() {
        persistCategory("Transporte", ana);
        persistCategory("Alimentação", ana);
        persistCategory("Lazer", beto);

        var categories = categoryRepository.findByUserId(ana.getId());

        assertEquals(Set.of("Alimentação", "Transporte"), categories.stream().map(Category::getName).collect(Collectors.toSet()));
    }

    @Test
    void findByUserIdAndNameIgnoreCase_shouldFindOnlyWithinTheUser() {
        persistCategory("Alimentação", ana);

        assertTrue(categoryRepository.findByUserIdAndNameIgnoreCase(ana.getId(), "ALIMENTAÇÃO").isPresent());
        assertTrue(categoryRepository.findByUserIdAndNameIgnoreCase(beto.getId(), "Alimentação").isEmpty());
    }

    @Test
    void findByIdAndUserId_shouldNotFindACategoryOfAnotherUser() {
        var category = persistCategory("Alimentação", ana);

        assertTrue(categoryRepository.findByIdAndUserId(category.getId(), ana.getId()).isPresent());
        assertTrue(categoryRepository.findByIdAndUserId(category.getId(), beto.getId()).isEmpty());
    }

    private User persistUser(String name, String email) {
        var user = User.builder()
            .name(name)
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build();
        return testEntityManager.persist(user);
    }

    private Category newCategory(String name, User user) {
        return Category.builder()
            .name(name)
            .user(user)
            .build();
    }

    private Category persistCategory(String name, User user) {
        return testEntityManager.persistAndFlush(newCategory(name, user));
    }

}
