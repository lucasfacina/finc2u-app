package br.com.finc2u.server.features.recurringexpense.service;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.card.service.CardAccountService;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.materializer.RecurringExpenseMaterializer;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseRepository;
import br.com.finc2u.server.features.recurringexpense.vo.RecurringExpenseCommand;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringExpenseServiceTest {

    @Mock
    private RecurringExpenseRepository recurringExpenseRepository;

    @Mock
    private UserService userService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private CardAccountService cardAccountService;

    @Mock
    private RecurringExpenseMaterializer recurringExpenseMaterializer;

    @InjectMocks
    private RecurringExpenseService recurringExpenseService;

    private User user;
    private UUID userId;
    private Category category;
    private UUID categoryId;
    private CardAccount card;
    private UUID cardId;
    private RecurringExpense saved;
    private UUID recurringExpenseId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
            .name("Ana")
            .email("ana@email.com")
            .build();
        user.setId(userId);

        categoryId = UUID.randomUUID();
        category = Category.builder()
            .name("Assinaturas")
            .user(user)
            .build();
        category.setId(categoryId);

        cardId = UUID.randomUUID();
        card = CardAccount.builder()
            .cardName("Nubank")
            .user(user)
            .build();
        card.setId(cardId);

        recurringExpenseId = UUID.randomUUID();
        saved = newModel("Spotify");
        saved.setId(recurringExpenseId);
        saved.setUser(user);
        saved.setCategory(category);
        saved.setCardAccount(card);
    }

    @Test
    void create_shouldAttachOwnerCategoryAndCardAndSave() {
        var model = newModel("Spotify");
        when(userService.getById(userId))
            .thenReturn(user);
        when(categoryService.getByIdAndUser(categoryId, userId))
            .thenReturn(category);
        when(cardAccountService.getByIdAndUser(cardId, userId))
            .thenReturn(card);
        when(recurringExpenseRepository.save(model))
            .thenReturn(saved);

        var response = recurringExpenseService.create(new RecurringExpenseCommand(model, categoryId, cardId), userId);

        assertSame(saved, response);
        assertSame(user, model.getUser());
        assertSame(category, model.getCategory());
        assertSame(card, model.getCardAccount());
    }

    // As despesas do mês corrente e do seguinte nascem junto com o modelo, na mesma transação.
    @Test
    void create_shouldMaterializeTheUpcomingMonthsOfTheSavedModel() {
        var model = newModel("Spotify");
        when(userService.getById(userId))
            .thenReturn(user);
        when(categoryService.getByIdAndUser(categoryId, userId))
            .thenReturn(category);
        when(recurringExpenseRepository.save(model))
            .thenReturn(saved);

        recurringExpenseService.create(new RecurringExpenseCommand(model, categoryId, null), userId);

        var inOrder = inOrder(recurringExpenseRepository, recurringExpenseMaterializer);
        inOrder.verify(recurringExpenseRepository).save(model);
        inOrder.verify(recurringExpenseMaterializer).materializeUpcoming(saved);
    }

    @Test
    void create_shouldLeaveTheCardEmpty_whenNoCardIsInformed() {
        var model = newModel("Aluguel");
        when(userService.getById(userId))
            .thenReturn(user);
        when(categoryService.getByIdAndUser(categoryId, userId))
            .thenReturn(category);
        when(recurringExpenseRepository.save(model))
            .thenReturn(model);

        recurringExpenseService.create(new RecurringExpenseCommand(model, categoryId, null), userId);

        assertNull(model.getCardAccount());
        verifyNoInteractions(cardAccountService);
    }

    // O 404 vem de getByIdAndUser, que só enxerga o que é do dono (CategoryRepositoryTest / CardAccountRepositoryTest).
    @Test
    void create_shouldPropagateNotFound_whenTheCategoryBelongsToAnotherUser() {
        when(userService.getById(userId))
            .thenReturn(user);
        when(categoryService.getByIdAndUser(categoryId, userId))
            .thenThrow(new ResourceNotFoundException("Categoria não encontrada"));
        var command = new RecurringExpenseCommand(newModel("Spotify"), categoryId, null);

        assertThrows(ResourceNotFoundException.class, () -> recurringExpenseService.create(command, userId));
        verify(recurringExpenseRepository, never()).save(any(RecurringExpense.class));
        verifyNoInteractions(recurringExpenseMaterializer);
    }

    @Test
    void create_shouldPropagateNotFound_whenTheCardBelongsToAnotherUser() {
        when(userService.getById(userId))
            .thenReturn(user);
        when(categoryService.getByIdAndUser(categoryId, userId))
            .thenReturn(category);
        when(cardAccountService.getByIdAndUser(cardId, userId))
            .thenThrow(new ResourceNotFoundException("Conta/Cartão não encontrado"));
        var command = new RecurringExpenseCommand(newModel("Spotify"), categoryId, cardId);

        assertThrows(ResourceNotFoundException.class, () -> recurringExpenseService.create(command, userId));
        verify(recurringExpenseRepository, never()).save(any(RecurringExpense.class));
    }

    @Test
    void getById_shouldThrowResourceNotFoundException_whenMissing() {
        when(recurringExpenseRepository.findById(recurringExpenseId))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> recurringExpenseService.getById(recurringExpenseId));
    }

    @Test
    void getByUser_shouldValidateTheUserAndReturnHisModels() {
        when(userService.getById(userId))
            .thenReturn(user);
        when(recurringExpenseRepository.findByUserIdOrderByDescriptionAsc(userId))
            .thenReturn(List.of(saved));

        var response = recurringExpenseService.getByUser(userId);

        assertEquals(List.of(saved), response);
    }

    @Test
    void getByUser_shouldThrowResourceNotFoundException_whenTheUserDoesNotExist() {
        when(userService.getById(userId))
            .thenThrow(new ResourceNotFoundException("Usuário não encontrado"));

        assertThrows(ResourceNotFoundException.class, () -> recurringExpenseService.getByUser(userId));
        verifyNoInteractions(recurringExpenseRepository);
    }

    @Test
    void update_shouldCopyTheEditableFieldsAndResolveTheReferencesFromTheOwner() {
        var newCategoryId = UUID.randomUUID();
        var newCategory = Category.builder()
            .name("Moradia")
            .user(user)
            .build();
        newCategory.setId(newCategoryId);
        var updates = RecurringExpense.of("Spotify Família", BigDecimal.valueOf(34.90), 10, YearMonth.of(2026, 11),
            YearMonth.of(2027, 5));
        when(recurringExpenseRepository.findById(recurringExpenseId))
            .thenReturn(Optional.of(saved));
        when(categoryService.getByIdAndUser(newCategoryId, userId))
            .thenReturn(newCategory);
        when(recurringExpenseRepository.save(saved))
            .thenReturn(saved);

        var response = recurringExpenseService.update(recurringExpenseId, new RecurringExpenseCommand(updates, newCategoryId, null));

        assertSame(saved, response);
        assertEquals("Spotify Família", saved.getDescription());
        assertEquals(BigDecimal.valueOf(34.90), saved.getValue());
        assertEquals(10, saved.getDayOfMonth());
        assertEquals(LocalDate.of(2026, 11, 1), saved.getStartMonth());
        assertEquals(LocalDate.of(2027, 5, 1), saved.getEndMonth());
        assertSame(newCategory, saved.getCategory());
        assertNull(saved.getCardAccount());
        assertSame(user, saved.getUser());
    }

    @Test
    void update_shouldThrowResourceNotFoundException_whenMissing() {
        when(recurringExpenseRepository.findById(recurringExpenseId))
            .thenReturn(Optional.empty());
        var command = new RecurringExpenseCommand(newModel("Spotify"), categoryId, null);

        assertThrows(ResourceNotFoundException.class, () -> recurringExpenseService.update(recurringExpenseId, command));
        verify(recurringExpenseRepository, never()).save(any(RecurringExpense.class));
    }

    @Test
    void delete_shouldRemoveTheModel() {
        when(recurringExpenseRepository.findById(recurringExpenseId))
            .thenReturn(Optional.of(saved));

        recurringExpenseService.delete(recurringExpenseId);

        verify(recurringExpenseRepository).delete(saved);
    }

    @Test
    void delete_shouldThrowResourceNotFoundException_whenMissing() {
        when(recurringExpenseRepository.findById(recurringExpenseId))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> recurringExpenseService.delete(recurringExpenseId));
        verify(recurringExpenseRepository, never()).delete(any(RecurringExpense.class));
    }

    private RecurringExpense newModel(String description) {
        return RecurringExpense.of(description, BigDecimal.valueOf(21.90), 5, YearMonth.of(2026, 10), null);
    }

}
