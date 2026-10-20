package br.com.finc2u.server.features.card.repository;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private User persistUser(String name, String email) {
        var user = User.builder()
            .name(name)
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build();
        return testEntityManager.persist(user);
    }

}
