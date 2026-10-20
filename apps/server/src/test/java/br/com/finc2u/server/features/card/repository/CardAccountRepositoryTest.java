package br.com.finc2u.server.features.card.repository;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.user.entity.User;
import org.hibernate.exception.ConstraintViolationException;
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
class CardAccountRepositoryTest {

    @Autowired
    private CardAccountRepository cardAccountRepository;

    @Autowired
    private TestEntityManager testEntityManager;

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
    void findByUserIdAndCardNameContainingIgnoreCaseOrderByCardNameAsc_shouldSearchPartiallyOnlyWithinTheUser() {
        persistCard("Nubank", ana);
        persistCard("Mercado Pago", ana);
        persistCard("Inter", ana);
        persistCard("Nubank Ultravioleta", beto);

        var found = cardAccountRepository.findByUserIdAndCardNameContainingIgnoreCaseOrderByCardNameAsc(ana.getId(), "NU");
        var all = cardAccountRepository.findByUserIdAndCardNameContainingIgnoreCaseOrderByCardNameAsc(ana.getId(), "");

        assertEquals(List.of("Nubank"), found.stream().map(CardAccount::getCardName).toList());
        assertEquals(List.of("Inter", "Mercado Pago", "Nubank"), all.stream().map(CardAccount::getCardName).toList());
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
