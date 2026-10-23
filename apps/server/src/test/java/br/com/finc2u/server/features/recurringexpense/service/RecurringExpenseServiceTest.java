package br.com.finc2u.server.features.recurringexpense.service;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.card.service.CardAccountService;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpenseSkip;
import br.com.finc2u.server.features.recurringexpense.materializer.RecurringExpenseMaterializer;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseRepository;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseSkipRepository;
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
    private RecurringExpenseSkipRepository recurringExpenseSkipRepository;

    @Mock
    private UserService userService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private CardAccountService cardAccountService;

    @Mock
    private ExpenseService expenseService;

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

        var response = recurringExpenseService.create(RecurringExpenseCommand.of(model).withCategoryId(categoryId).withCardAccountId(cardId), userId);

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

        recurringExpenseService.create(RecurringExpenseCommand.of(model).withCategoryId(categoryId), userId);

        var inOrder = inOrder(recurringExpenseRepository, recurringExpenseMaterializer);
        inOrder.verify(recurringExpenseRepository).save(model);
        inOrder.verify(recurringExpenseMaterializer).materializeUpcoming(saved);
    }

    // A categoria por nome é resolvida (e criada, se faltar) entre as do dono; o id nem é consultado.
    @Test
    void create_shouldResolveOrCreateTheCategoryByName_whenOnlyTheNameIsInformed() {
        var model = newModel("Ração");
        when(userService.getById(userId))
            .thenReturn(user);
        when(categoryService.getOrCreateByName(userId, "Pets"))
            .thenReturn(category);
        when(recurringExpenseRepository.save(model))
            .thenReturn(saved);

        recurringExpenseService.create(RecurringExpenseCommand.of(model).withCategoryName("Pets"), userId);

        assertSame(category, model.getCategory());
        verify(categoryService, never()).getByIdAndUser(any(), any());
    }

    @Test
    void update_shouldResolveOrCreateTheCategoryByName_whenOnlyTheNameIsInformed() {
        when(recurringExpenseRepository.findById(recurringExpenseId))
            .thenReturn(Optional.of(saved));
        when(categoryService.getOrCreateByName(userId, "Pets"))
            .thenReturn(category);
        when(recurringExpenseRepository.save(saved))
            .thenReturn(saved);

        recurringExpenseService.update(recurringExpenseId, RecurringExpenseCommand.of(newModel("Spotify")).withCategoryName("Pets"));

        assertSame(category, saved.getCategory());
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

        recurringExpenseService.create(RecurringExpenseCommand.of(model).withCategoryId(categoryId), userId);

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
        var command = RecurringExpenseCommand.of(newModel("Spotify")).withCategoryId(categoryId);

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
        var command = RecurringExpenseCommand.of(newModel("Spotify")).withCategoryId(categoryId).withCardAccountId(cardId);

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

    // O mês é o filtro: modelo sem término, ou que termina nele ou depois, entra; o repositório recebe o dia 1 do mês.
    @Test
    void getActiveIn_shouldReturnTheModelsThatHaveNotEndedBeforeTheMonth() {
        when(recurringExpenseRepository.findByEndMonthIsNullOrEndMonthGreaterThanEqual(LocalDate.of(2026, 10, 1)))
            .thenReturn(List.of(saved));

        var response = recurringExpenseService.getActiveIn(YearMonth.of(2026, 10));

        assertEquals(List.of(saved), response);
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

        var response = recurringExpenseService.update(recurringExpenseId, RecurringExpenseCommand.of(updates).withCategoryId(newCategoryId));

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
        var command = RecurringExpenseCommand.of(newModel("Spotify")).withCategoryId(categoryId);

        assertThrows(ResourceNotFoundException.class, () -> recurringExpenseService.update(recurringExpenseId, command));
        verify(recurringExpenseRepository, never()).save(any(RecurringExpense.class));
    }

    // As despesas geradas ficam (sem modelo) e os pulos do modelo saem junto: os dois antes de apagar o modelo (FK).
    @Test
    void delete_shouldDetachTheGeneratedExpensesAndRemoveTheSkipsBeforeRemovingTheModel() {
        when(recurringExpenseRepository.findById(recurringExpenseId))
            .thenReturn(Optional.of(saved));

        recurringExpenseService.delete(recurringExpenseId);

        var inOrder = inOrder(expenseService, recurringExpenseSkipRepository, recurringExpenseRepository);
        inOrder.verify(expenseService).detachFromRecurringExpense(recurringExpenseId);
        inOrder.verify(recurringExpenseSkipRepository).deleteByRecurringExpenseId(recurringExpenseId);
        inOrder.verify(recurringExpenseRepository).delete(saved);
    }

    @Test
    void delete_shouldThrowResourceNotFoundException_whenMissing() {
        when(recurringExpenseRepository.findById(recurringExpenseId))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> recurringExpenseService.delete(recurringExpenseId));
        verify(recurringExpenseRepository, never()).delete(any(RecurringExpense.class));
        verifyNoInteractions(expenseService, recurringExpenseSkipRepository);
    }

    // ---- mês pulado: o usuário apagou a despesa que o modelo gerou nesse vencimento ----

    @Test
    void skipMonth_shouldRecordTheDueDateOfTheModel() {
        var dueDate = LocalDate.of(2026, 10, 25);
        when(recurringExpenseRepository.findById(recurringExpenseId))
            .thenReturn(Optional.of(saved));
        when(recurringExpenseSkipRepository.existsByRecurringExpenseIdAndDueDate(recurringExpenseId, dueDate))
            .thenReturn(false);

        recurringExpenseService.skipMonth(recurringExpenseId, dueDate);

        var captor = org.mockito.ArgumentCaptor.forClass(RecurringExpenseSkip.class);
        verify(recurringExpenseSkipRepository).save(captor.capture());
        assertSame(saved, captor.getValue().getRecurringExpense());
        assertEquals(dueDate, captor.getValue().getDueDate());
    }

    // Pular de novo o mesmo vencimento (a despesa voltou e foi apagada outra vez) não duplica o pulo.
    @Test
    void skipMonth_shouldNotRecordTwice_whenTheDueDateIsAlreadySkipped() {
        var dueDate = LocalDate.of(2026, 10, 25);
        when(recurringExpenseRepository.findById(recurringExpenseId))
            .thenReturn(Optional.of(saved));
        when(recurringExpenseSkipRepository.existsByRecurringExpenseIdAndDueDate(recurringExpenseId, dueDate))
            .thenReturn(true);

        recurringExpenseService.skipMonth(recurringExpenseId, dueDate);

        verify(recurringExpenseSkipRepository, never()).save(any(RecurringExpenseSkip.class));
    }

    // Modelo que já não existe não tem o que pular, e isso não pode impedir o usuário de apagar a despesa.
    @Test
    void skipMonth_shouldDoNothing_whenTheModelNoLongerExists() {
        when(recurringExpenseRepository.findById(recurringExpenseId))
            .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> recurringExpenseService.skipMonth(recurringExpenseId, LocalDate.of(2026, 10, 25)));

        verifyNoInteractions(recurringExpenseSkipRepository);
    }

    private RecurringExpense newModel(String description) {
        return RecurringExpense.of(description, BigDecimal.valueOf(21.90), 5, YearMonth.of(2026, 10), null);
    }

}
