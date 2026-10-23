package br.com.finc2u.server.features.card.repository;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.card.form.CardAccountResponse;
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
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class CardAccountRepositoryTest {

    @Autowired
    private CardAccountRepository cardAccountRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private EntityManager entityManager;

    private User ana;
    private User beto;

    @BeforeEach
    void setUp() {
        ana = persistUser("Ana", "ana@email.com");
        beto = persistUser("Beto", "beto@email.com");
    }

    @Test
    void findByIdAndUserId_shouldNotFindACardOfAnotherUser() {
        var card = testEntityManager.persistAndFlush(CardAccount.builder()
            .cardName("Nubank")
            .closingDate(9)
            .dueDate(15)
            .user(ana)
            .build());

        assertTrue(cardAccountRepository.findByIdAndUserId(card.getId(), ana.getId()).isPresent());
        assertTrue(cardAccountRepository.findByIdAndUserId(card.getId(), beto.getId()).isEmpty());
    }

    // Trava a premissa do GlobalExceptionHandler: nome duplicado no mesmo usuário chega como violação do tipo UNIQUE.
    @Test
    void save_shouldFailWithUniqueViolation_whenNameIsDuplicatedForTheSameUser() {
        persistCard("Nubank", ana);

        var exception = assertThrows(DataIntegrityViolationException.class,
            () -> cardAccountRepository.saveAndFlush(newCard("Nubank", ana)));

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
        persistCard("Nubank", ana);

        assertDoesNotThrow(() -> cardAccountRepository.saveAndFlush(newCard("Nubank", beto)));
    }

    @Test
    void findByUserIdAndCardNameIgnoreCase_shouldFindOnlyWithinTheUser() {
        persistCard("Nubank", ana);

        assertTrue(cardAccountRepository.findByUserIdAndCardNameIgnoreCase(ana.getId(), "NUBANK").isPresent());
        assertTrue(cardAccountRepository.findByUserIdAndCardNameIgnoreCase(beto.getId(), "Nubank").isEmpty());
    }

    @Test
    void findByUserIdAndCardNameContainingIgnoreCase_shouldSearchPartiallyOnlyWithinTheUser() {
        persistCard("Nubank", ana);
        persistCard("Mercado Pago", ana);
        persistCard("Inter", ana);
        persistCard("Nubank Ultravioleta", beto);

        var found = cardAccountRepository.findByUserIdAndCardNameContainingIgnoreCase(ana.getId(), "NU");
        var all = cardAccountRepository.findByUserIdAndCardNameContainingIgnoreCase(ana.getId(), "");

        assertEquals(List.of("Nubank"), found.stream().map(CardAccount::getCardName).toList());
        assertEquals(Set.of("Inter", "Mercado Pago", "Nubank"), all.stream().map(CardAccount::getCardName).collect(Collectors.toSet()));
    }

    /*
     * O CardAccountResponse lê o nome do dono (LAZY): o @EntityGraph(user) o traz na própria consulta. Sem ele, cada
     * listagem custaria 1 + 1 statements (a carga do dono). Mede com Response montado dentro da medição.
     */
    @Test
    void findByUserIdAndCardNameContaining_shouldServeTheListingWithTheOwnerInASingleStatement() {
        persistCard("Nubank", ana);
        persistCard("Mercado Pago", ana);
        persistCard("Inter", ana);
        persistCard("Cartão do Beto", beto);
        var statistics = resetStatistics();

        var responses = cardAccountRepository.findByUserIdAndCardNameContainingIgnoreCase(ana.getId(), "")
            .stream()
            .map(CardAccountResponse::from)
            .toList();

        assertEquals(3, responses.size());
        assertTrue(responses.stream().allMatch(response -> "Ana".equals(response.userName())));
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    @Test
    void findById_shouldLoadTheOwnerInASingleStatement() {
        var card = persistCard("Nubank", ana);
        var statistics = resetStatistics();

        var response = CardAccountResponse.from(cardAccountRepository.findById(card.getId()).orElseThrow());

        assertEquals("Ana", response.userName());
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    @Test
    void findByUserIdAndCardNameContaining_shouldNotGrowWithTheNumberOfCards() {
        persistCard("Nubank", ana);
        persistCard("Inter", ana);
        var statistics = resetStatistics();
        cardAccountRepository.findByUserIdAndCardNameContainingIgnoreCase(ana.getId(), "")
            .forEach(CardAccountResponse::from);
        var statementsWithFewCards = statistics.getPrepareStatementCount();

        for (var number = 1; number <= 6; number++) {
            persistCard("Cartão " + number, ana);
        }
        resetStatistics();
        cardAccountRepository.findByUserIdAndCardNameContainingIgnoreCase(ana.getId(), "")
            .forEach(CardAccountResponse::from);

        assertEquals(statementsWithFewCards, statistics.getPrepareStatementCount(),
            "Mais cartões não podem gerar mais statements (N+1)");
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

    private CardAccount newCard(String name, User user) {
        return CardAccount.builder()
            .cardName(name)
            .closingDate(9)
            .dueDate(15)
            .user(user)
            .build();
    }

    private CardAccount persistCard(String name, User user) {
        return testEntityManager.persistAndFlush(newCard(name, user));
    }

    private User persistUser(String name, String email) {
        var user = User.builder()
            .name(name)
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build();
        return testEntityManager.persist(user);
    }

}
