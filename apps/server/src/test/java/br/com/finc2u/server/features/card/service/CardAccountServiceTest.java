package br.com.finc2u.server.features.card.service;

import br.com.finc2u.server.exception.BusinessException;
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

import java.time.LocalDate;
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
    void getByIdAndUser_shouldReturnCard_whenItBelongsToTheUser() {
        when(cardAccountRepository.findByIdAndUserId(cardId, userId))
            .thenReturn(Optional.of(cardAccount));

        assertSame(cardAccount, cardAccountService.getByIdAndUser(cardId, userId));
    }

    @Test
    void getByIdAndUser_shouldThrowResourceNotFoundException_whenCardBelongsToAnotherUser() {
        when(cardAccountRepository.findByIdAndUserId(cardId, userId))
            .thenReturn(Optional.empty());

        var exception = assertThrows(ResourceNotFoundException.class,
            () -> cardAccountService.getByIdAndUser(cardId, userId));

        assertEquals("Conta/Cartão não encontrado com o ID: " + cardId, exception.getMessage());
    }

    @Test
    void getByUserAndName_shouldReturnTheCardOfTheUser_ignoringCase() {
        when(cardAccountRepository.findByUserIdAndCardNameIgnoreCase(userId, "nubank"))
            .thenReturn(Optional.of(cardAccount));

        assertSame(cardAccount, cardAccountService.getByUserAndName(userId, "nubank"));
    }

    // Cartão de outro usuário não volta da consulta escopada: o erro é o mesmo de nome inexistente.
    @Test
    void getByUserAndName_shouldThrowResourceNotFoundException_whenNoCardOfTheUserHasThatName() {
        when(cardAccountRepository.findByUserIdAndCardNameIgnoreCase(userId, "Nubank"))
            .thenReturn(Optional.empty());

        var exception = assertThrows(ResourceNotFoundException.class,
            () -> cardAccountService.getByUserAndName(userId, "Nubank"));

        assertEquals("Conta/Cartão não encontrado com o nome: Nubank", exception.getMessage());
    }

    @Test
    void create_shouldReturnSavedCardAccount_whenUserExists() {
        when(userService.getById(userId))
            .thenReturn(user);
        when(cardAccountRepository.save(any(CardAccount.class)))
            .thenReturn(cardAccount);

        var response = cardAccountService.create(cardAccount, userId);

        assertNotNull(response);
        assertEquals(cardAccount.getCardName(), response.getCardName());
        assertEquals(user, response.getUser());
        verify(cardAccountRepository, times(1)).save(any(CardAccount.class));
    }

    // "Mesmo nome em outro usuário é aceito" é garantido pela consulta escopada por usuário (CardAccountRepositoryTest).
    @Test
    void create_shouldThrowBusinessException_whenNameAlreadyExistsForTheUser_ignoringCase() {
        var newCard = CardAccount.of("NUBANK", 5, 15, null);
        when(userService.getById(userId))
            .thenReturn(user);
        when(cardAccountRepository.findByUserIdAndCardNameIgnoreCase(userId, "NUBANK"))
            .thenReturn(Optional.of(cardAccount));

        assertThrows(BusinessException.class, () -> cardAccountService.create(newCard, userId));
        verify(cardAccountRepository, never()).save(any(CardAccount.class));
    }

    @Test
    void getByUser_shouldSearchByTheTrimmedName_whenNameIsInformed() {
        when(userService.getById(userId))
            .thenReturn(user);
        when(cardAccountRepository.findByUserIdAndCardNameContainingIgnoreCaseOrderByCardNameAsc(userId, "nu"))
            .thenReturn(List.of(cardAccount));

        var response = cardAccountService.getByUser(userId, "  nu ");

        assertEquals(List.of(cardAccount), response);
    }

    @Test
    void getById_shouldReturnCardAccount_whenIdExists() {
        when(cardAccountRepository.findById(cardId))
            .thenReturn(Optional.of(cardAccount));

        var response = cardAccountService.getById(cardId);

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
        when(cardAccountRepository.findByUserIdAndCardNameContainingIgnoreCaseOrderByCardNameAsc(userId, ""))
            .thenReturn(List.of(cardAccount));

        var response = cardAccountService.getByUser(userId, null);

        assertNotNull(response);
        assertFalse(response.isEmpty());
        assertEquals(1, response.size());
        verify(userService, times(1)).getById(userId);
    }

    @Test
    void update_shouldReturnUpdatedCardAccount_whenExists() {
        var updates = CardAccount.builder()
            .cardName("C6Bank")
            .expirationDate(LocalDate.of(2031, 8, 31))
            .build();

        when(cardAccountRepository.findById(cardId))
            .thenReturn(Optional.of(cardAccount));
        when(cardAccountRepository.save(any(CardAccount.class)))
            .thenReturn(cardAccount);

        var response = cardAccountService.update(cardId, updates);

        assertNotNull(response);
        assertEquals("C6Bank", cardAccount.getCardName());
        assertEquals(LocalDate.of(2031, 8, 31), cardAccount.getExpirationDate());
        verify(cardAccountRepository, times(1)).save(cardAccount);
    }

    @Test
    void update_shouldThrowBusinessException_whenNameAlreadyExistsOnAnotherCardOfTheUser() {
        var other = CardAccount.builder()
            .cardName("C6Bank")
            .user(user)
            .build();
        other.setId(UUID.randomUUID());
        when(cardAccountRepository.findById(cardId))
            .thenReturn(Optional.of(cardAccount));
        when(cardAccountRepository.findByUserIdAndCardNameIgnoreCase(userId, "c6bank"))
            .thenReturn(Optional.of(other));

        assertThrows(BusinessException.class,
            () -> cardAccountService.update(cardId, CardAccount.of("c6bank", 5, 15, null)));
        verify(cardAccountRepository, never()).save(any(CardAccount.class));
    }

    @Test
    void update_shouldAllowSameName_whenRenameToOwnCurrentName() {
        when(cardAccountRepository.findById(cardId))
            .thenReturn(Optional.of(cardAccount));
        when(cardAccountRepository.findByUserIdAndCardNameIgnoreCase(userId, "Nubank"))
            .thenReturn(Optional.of(cardAccount));
        when(cardAccountRepository.save(cardAccount))
            .thenReturn(cardAccount);

        assertDoesNotThrow(() -> cardAccountService.update(cardId, CardAccount.of("Nubank", 5, 15, null)));
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
