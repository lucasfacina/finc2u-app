package br.com.finc2u.server.features.card.service;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.card.repository.CardAccountRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CardAccountServiceTest {

    @Mock
    private CardAccountRepository cardAccountRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private CardAccountService cardAccountService;

    private User user;
    private CardAccount cardAccount;
    private UUID userId;
    private UUID cardId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        cardId = UUID.randomUUID();

        user = User.builder()
                .name("Lucas Facina")
                .email("facina3@gmail.com")
                .build();
        user.setId(userId);

        cardAccount = CardAccount.builder()
                .cardName("Nubank")
                .closingDate(5)
                .dueDate(15)
                .user(user)
                .build();
        cardAccount.setId(cardId);
    }

    @Test
    void create_shouldReturnSavedCardAccount_whenUserExists() {
        when(userService.getById(userId))
                .thenReturn(user);
        when(cardAccountRepository.save(any(CardAccount.class)))
                .thenReturn(cardAccount);

        CardAccount response = cardAccountService.create(cardAccount, userId);

        assertNotNull(response);
        assertEquals(cardAccount.getCardName(), response.getCardName());
        assertEquals(user, response.getUser());
        verify(cardAccountRepository, times(1)).save(any(CardAccount.class));
    }

    @Test
    void getById_shouldReturnCardAccount_whenIdExists() {
        when(cardAccountRepository.findById(cardId))
                .thenReturn(Optional.of(cardAccount));

        CardAccount response = cardAccountService.getById(cardId);

        assertNotNull(response);
        assertEquals(cardId, response.getId());
    }

    @Test
    void getById_shouldThrowResourceNotFoundException_whenIdDoesNotExist() {
        when(cardAccountRepository.findById(cardId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cardAccountService.getById(cardId));
    }

    @Test
    void getByUser_shouldReturnListOfCardAccount_whenUserExists() {
        when(userService.getById(userId))
                .thenReturn(user);
        when(cardAccountRepository.findByUserIdOrderByCardNameAsc(userId))
                .thenReturn(List.of(cardAccount));

        List<CardAccount> response = cardAccountService.getByUser(userId);

        assertNotNull(response);
        assertFalse(response.isEmpty());
        assertEquals(1, response.size());
        verify(userService, times(1)).getById(userId);
    }

    @Test
    void update_shouldReturnUpdatedCardAccount_whenExists() {
        CardAccount updates = CardAccount.builder()
                .cardName("C6Bank")
                .build();

        when(cardAccountRepository.findById(cardId))
                .thenReturn(Optional.of(cardAccount));
        when(cardAccountRepository.save(any(CardAccount.class)))
                .thenReturn(cardAccount);

        CardAccount response = cardAccountService.update(cardId, updates);

        assertNotNull(response);
        assertEquals("C6Bank", cardAccount.getCardName());
        verify(cardAccountRepository, times(1)).save(cardAccount);
    }

    @Test
    void delete_shouldCallDelete_whenExists() {
        when(cardAccountRepository.findById(cardId))
                .thenReturn(Optional.of(cardAccount));
        doNothing().when(cardAccountRepository).delete(cardAccount);

        cardAccountService.delete(cardId);

        verify(cardAccountRepository, times(1)).delete(cardAccount);
    }

}
