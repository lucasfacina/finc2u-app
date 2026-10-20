package br.com.finc2u.server.features.tag.repository;

import br.com.finc2u.server.features.tag.entity.Tag;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.entity.UserConfiguration;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class TagRepositoryTest {

    @Autowired
    private TagRepository tagRepository;

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
        persistTag("Essencial", ana);

        var exception = assertThrows(DataIntegrityViolationException.class,
            () -> tagRepository.saveAndFlush(newTag("Essencial", ana)));

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
        persistTag("Essencial", ana);

        assertDoesNotThrow(() -> tagRepository.saveAndFlush(newTag("Essencial", beto)));
    }

    @Test
    void findByUserIdOrderByNameAsc_shouldReturnOnlyTagsOfTheUser_sortedByName() {
        persistTag("Recorrente", ana);
        persistTag("Dividido", ana);
        persistTag("Essencial", beto);

        var tags = tagRepository.findByUserIdOrderByNameAsc(ana.getId());

        assertEquals(List.of("Dividido", "Recorrente"), tags.stream().map(Tag::getName).toList());
    }

    @Test
    void findByUserIdAndNameIgnoreCase_shouldFindOnlyWithinTheUser() {
        persistTag("Essencial", ana);

        assertTrue(tagRepository.findByUserIdAndNameIgnoreCase(ana.getId(), "ESSENCIAL").isPresent());
        assertTrue(tagRepository.findByUserIdAndNameIgnoreCase(beto.getId(), "Essencial").isEmpty());
    }

    @Test
    void findByUserIdAndNameIn_shouldReturnOnlyTagsOfTheUser() {
        persistTag("Essencial", ana);
        persistTag("Dividido", ana);
        persistTag("Essencial", beto);

        var tags = tagRepository.findByUserIdAndNameIn(ana.getId(), List.of("Essencial", "Recorrente"));

        assertEquals(1, tags.size());
        assertEquals(ana.getId(), tags.getFirst().getUser().getId());
    }

    @Test
    void findByIdInAndUserId_shouldIgnoreTagsOfAnotherUser() {
        var anaTag = persistTag("Essencial", ana);
        var betoTag = persistTag("Dividido", beto);

        var tags = tagRepository.findByIdInAndUserId(Set.of(anaTag.getId(), betoTag.getId()), ana.getId());

        assertEquals(List.of(anaTag), tags);
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

    private Tag newTag(String name, User user) {
        return Tag.builder()
            .name(name)
            .user(user)
            .build();
    }

    private Tag persistTag(String name, User user) {
        return testEntityManager.persistAndFlush(newTag(name, user));
    }

}
