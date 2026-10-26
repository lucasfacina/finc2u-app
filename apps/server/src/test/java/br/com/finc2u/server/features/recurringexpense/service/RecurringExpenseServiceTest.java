package br.com.finc2u.server.features.recurringexpense.service;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpenseSkip;
import br.com.finc2u.server.features.recurringexpense.mapper.RecurringExpenseMapper;
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
import java.util.stream.Stream;

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
    private ExpenseService expenseService;

    @Mock
    private RecurringExpenseMapper recurringExpenseMapper;

    @Mock
    private RecurringExpenseMaterializer recurringExpenseMaterializer;

    @InjectMocks
    private RecurringExpenseService recurringExpenseService;

    private User user;
    private UUID userId;
    private UUID categoryId;
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
        var category = Category.builder()
            .name("Assinaturas")
            .user(user)
            .build();
        category.setId(categoryId);

        cardId = UUID.randomUUID();
        var card = CardAccount.builder()
            .cardName("Nubank")
            .user(user)
            .build();
        card.setId(cardId);

        recurringExpenseId = UUID.randomUUID();
        saved = newModel();
        saved.setId(recurringExpenseId);
        saved.setUser(user);
        saved.setCategory(category);
        saved.setCardAccount(card);
    }

    // O mapper precisa do dono do modelo para resolver categoria e cartão: o service o define antes.
    @Test
    void create_shouldAttachTheOwnerBeforeApplyingTheReferencesAndSave() {
        var model = newModel();
        var command = RecurringExpenseCommand.of(model).withCategoryId(categoryId).withCardAccountId(cardId);
        when(userService.getById(userId))
            .thenReturn(user);
        doAnswer(invocation -> {
            assertSame(user, model.getUser(), "o dono deve estar definido quando as referências são resolvidas");
            return null;
        }).when(recurringExpenseMapper).applyReferences(model, command);
        when(recurringExpenseRepository.save(model))
            .thenReturn(saved);

        var response = recurringExpenseService.create(command, userId);

        assertSame(saved, response);
        assertSame(user, model.getUser());
        verify(recurringExpenseMapper).applyReferences(model, command);
    }

    // As despesas do mês corrente e do seguinte nascem junto com o modelo, na mesma transação.
    @Test
    void create_shouldMaterializeTheUpcomingMonthsOfTheSavedModel() {
        var model = newModel();
        when(userService.getById(userId))
            .thenReturn(user);
        when(recurringExpenseRepository.save(model))
            .thenReturn(saved);

        recurringExpenseService.create(RecurringExpenseCommand.of(model).withCategoryId(categoryId), userId);

        var inOrder = inOrder(recurringExpenseRepository, recurringExpenseMaterializer);
        inOrder.verify(recurringExpenseRepository).save(model);
        inOrder.verify(recurringExpenseMaterializer).materializeUpcoming(saved);
    }

    @Test
    void create_shouldNotSaveNorMaterialize_whenTheMapperRejectsAReference() {
        var model = newModel();
        var command = RecurringExpenseCommand.of(model).withCategoryId(categoryId);
        when(userService.getById(userId))
            .thenReturn(user);
        doThrow(new ResourceNotFoundException("Categoria não encontrada"))
            .when(recurringExpenseMapper).applyReferences(model, command);

        assertThrows(ResourceNotFoundException.class, () -> recurringExpenseService.create(command, userId));
        verify(recurringExpenseRepository, never()).save(any(RecurringExpense.class));
        verifyNoInteractions(recurringExpenseMaterializer);
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
        when(recurringExpenseRepository.findByUserId(userId))
            .thenReturn(List.of(saved));

        var response = recurringExpenseService.getByUser(userId);

        assertEquals(List.of(saved), response);
    }

    // A ordem não vem do banco: o service ordena em português, sem depender da collation do servidor.
    @Test
    void getByUser_shouldSortTheDescriptionsInPortugueseOrder_whateverTheOrderTheRepositoryReturns() {
        when(userService.getById(userId))
            .thenReturn(user);
        when(recurringExpenseRepository.findByUserId(userId))
            .thenReturn(Stream.of("Zebra", "ônibus", "Alimentação", "Água", "Ótica", "alimentação")
                .map(description -> RecurringExpense.of(description, BigDecimal.TEN, 5, YearMonth.of(2026, 10), null))
                .toList());

        var response = recurringExpenseService.getByUser(userId);

        assertEquals(List.of("Água", "alimentação", "Alimentação", "ônibus", "Ótica", "Zebra"), response.stream().map(RecurringExpense::getDescription).toList());
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
    void update_shouldApplyTheEditableFieldsAndThenTheReferences_andSave() {
        var updates = RecurringExpense.of("Spotify Família", BigDecimal.valueOf(34.90), 10, YearMonth.of(2026, 11),
            YearMonth.of(2027, 5));
        var command = RecurringExpenseCommand.of(updates).withCategoryId(categoryId);
        when(recurringExpenseRepository.findById(recurringExpenseId))
            .thenReturn(Optional.of(saved));
        when(recurringExpenseRepository.save(saved))
            .thenReturn(saved);

        var response = recurringExpenseService.update(recurringExpenseId, command);

        assertSame(saved, response);
        var inOrder = inOrder(recurringExpenseMapper, recurringExpenseRepository);
        inOrder.verify(recurringExpenseMapper).applyEditableFields(saved, updates);
        inOrder.verify(recurringExpenseMapper).applyReferences(saved, command);
        inOrder.verify(recurringExpenseRepository).save(saved);
    }

    @Test
    void update_shouldThrowResourceNotFoundException_whenMissing() {
        when(recurringExpenseRepository.findById(recurringExpenseId))
            .thenReturn(Optional.empty());
        var command = RecurringExpenseCommand.of(newModel()).withCategoryId(categoryId);

        assertThrows(ResourceNotFoundException.class, () -> recurringExpenseService.update(recurringExpenseId, command));
        verify(recurringExpenseRepository, never()).save(any(RecurringExpense.class));
        verifyNoInteractions(recurringExpenseMapper);
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

    // ---- mês pulado: o usuário apagou a despesa que o modelo gerou nesse mês ----

    @Test
    void skipMonth_shouldRecordTheMonthOfTheModel() {
        var month = YearMonth.of(2026, 10);
        when(recurringExpenseRepository.findById(recurringExpenseId))
            .thenReturn(Optional.of(saved));
        when(recurringExpenseSkipRepository.existsByRecurringExpenseIdAndRecurrenceMonth(recurringExpenseId, month.atDay(1)))
            .thenReturn(false);

        recurringExpenseService.skipMonth(recurringExpenseId, month);

        var captor = org.mockito.ArgumentCaptor.forClass(RecurringExpenseSkip.class);
        verify(recurringExpenseSkipRepository).save(captor.capture());
        assertSame(saved, captor.getValue().getRecurringExpense());
        assertEquals(LocalDate.of(2026, 10, 1), captor.getValue().getRecurrenceMonth());
    }

    // Pular de novo o mesmo mês (a despesa voltou e foi apagada outra vez) não duplica o pulo.
    @Test
    void skipMonth_shouldNotRecordTwice_whenTheMonthIsAlreadySkipped() {
        var month = YearMonth.of(2026, 10);
        when(recurringExpenseRepository.findById(recurringExpenseId))
            .thenReturn(Optional.of(saved));
        when(recurringExpenseSkipRepository.existsByRecurringExpenseIdAndRecurrenceMonth(recurringExpenseId, month.atDay(1)))
            .thenReturn(true);

        recurringExpenseService.skipMonth(recurringExpenseId, month);

        verify(recurringExpenseSkipRepository, never()).save(any(RecurringExpenseSkip.class));
    }

    // Modelo que já não existe não tem o que pular, e isso não pode impedir o usuário de apagar a despesa.
    @Test
    void skipMonth_shouldDoNothing_whenTheModelNoLongerExists() {
        when(recurringExpenseRepository.findById(recurringExpenseId))
            .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> recurringExpenseService.skipMonth(recurringExpenseId, YearMonth.of(2026, 10)));

        verifyNoInteractions(recurringExpenseSkipRepository);
    }

    private RecurringExpense newModel() {
        return RecurringExpense.of("Spotify", BigDecimal.valueOf(21.90), 5, YearMonth.of(2026, 10), null);
    }

}
