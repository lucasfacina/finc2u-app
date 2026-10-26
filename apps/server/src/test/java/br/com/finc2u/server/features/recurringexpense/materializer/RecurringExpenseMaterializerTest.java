package br.com.finc2u.server.features.recurringexpense.materializer;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.expense.vo.RecurrenceExpenseCommand;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseRepository;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseSkipRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.shared.event.PeriodDataChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringExpenseMaterializerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final YearMonth CURRENT_MONTH = YearMonth.of(2026, 10);

    @Mock
    private ExpenseService expenseService;

    @Mock
    private RecurringExpenseRepository recurringExpenseRepository;

    @Mock
    private RecurringExpenseSkipRepository recurringExpenseSkipRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    @SuppressWarnings("unused") // injetado no materializer pelo @InjectMocks
    private Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @InjectMocks
    private RecurringExpenseMaterializer materializer;

    private User user;
    private Category category;
    private RecurringExpense recurringExpense;

    @BeforeEach
    void setUp() {
        user = User.builder()
            .name("Ana")
            .build();
        user.setId(UUID.randomUUID());
        category = Category.builder()
            .name("Assinaturas")
            .build();

        recurringExpense = RecurringExpense.of("Spotify", BigDecimal.valueOf(21.90), 25, YearMonth.of(2026, 8), null);
        recurringExpense.setId(UUID.randomUUID());
        recurringExpense.setUser(user);
        recurringExpense.setCategory(category);
    }

    @Test
    void materialize_shouldAskTheExpenseServiceForTheExpenseOfTheDueDateOfTheMonth() {
        var expense = Expense.builder()
            .build();
        when(expenseService.createFromRecurrence(commandFor(recurringExpense, LocalDate.of(2026, 10, 25))))
            .thenReturn(Optional.of(expense));

        var created = materializer.materialize(recurringExpense, CURRENT_MONTH);

        assertSame(expense, created.orElseThrow());
    }

    @Test
    void materialize_shouldHandTheExpenseServiceTheDataOfTheModelAndNotTheModelItself() {
        var card = CardAccount.builder()
            .cardName("Nubank")
            .closingDate(3)
            .dueDate(10)
            .build();
        recurringExpense.setCardAccount(card);
        var captor = org.mockito.ArgumentCaptor.forClass(RecurrenceExpenseCommand.class);

        materializer.materialize(recurringExpense, CURRENT_MONTH);

        verify(expenseService).createFromRecurrence(captor.capture());
        var command = captor.getValue();
        assertEquals(recurringExpense.getId(), command.recurringExpenseId());
        assertEquals("Spotify", command.description());
        assertEquals(BigDecimal.valueOf(21.90), command.value());
        assertEquals(LocalDate.of(2026, 11, 10), command.dueDate());
        assertSame(user, command.user());
        assertSame(category, command.category());
        assertSame(card, command.cardAccount());
    }

    // Rodar duas vezes não duplica: na segunda, o service diz que a despesa do vencimento já existe.
    @Test
    void materialize_shouldReturnEmpty_whenTheExpenseOfTheDueDateAlreadyExists() {
        when(expenseService.createFromRecurrence(commandFor(recurringExpense, LocalDate.of(2026, 10, 25))))
            .thenReturn(Optional.empty());

        var created = materializer.materialize(recurringExpense, CURRENT_MONTH);

        assertTrue(created.isEmpty());
    }

    @Test
    void materialize_shouldUseTheInvoiceDueDate_whenThereIsACard() {
        recurringExpense.setCardAccount(CardAccount.builder()
            .cardName("Nubank")
            .closingDate(3)
            .dueDate(10)
            .build());

        materializer.materialize(recurringExpense, CURRENT_MONTH);

        verify(expenseService).createFromRecurrence(commandFor(recurringExpense, CURRENT_MONTH, LocalDate.of(2026, 11, 10)));
    }

    @Test
    void materialize_shouldUseTheLastDayOfAShortMonth() {
        recurringExpense.setDayOfMonth(31);

        materializer.materialize(recurringExpense, YearMonth.of(2026, 11));

        verify(expenseService).createFromRecurrence(commandFor(recurringExpense, LocalDate.of(2026, 11, 30)));
    }

    @Test
    void materialize_shouldNotCreateAnything_beforeTheStartMonth() {
        recurringExpense.setStartMonth(LocalDate.of(2026, 12, 1));

        var created = materializer.materialize(recurringExpense, YearMonth.of(2026, 11));

        assertTrue(created.isEmpty());
        verifyNoInteractions(expenseService);
    }

    @Test
    void materialize_shouldNotCreateAnything_afterTheEndMonth() {
        recurringExpense.setEndMonth(LocalDate.of(2026, 10, 1));

        var created = materializer.materialize(recurringExpense, YearMonth.of(2026, 11));

        assertTrue(created.isEmpty());
        verifyNoInteractions(expenseService);
    }

    @Test
    void materialize_shouldCreateTheLastMonthOfTheModel() {
        recurringExpense.setEndMonth(LocalDate.of(2026, 11, 1));

        materializer.materialize(recurringExpense, YearMonth.of(2026, 11));

        verify(expenseService).createFromRecurrence(commandFor(recurringExpense, LocalDate.of(2026, 11, 25)));
    }

    // Sem retroativo: o mês passado não vira despesa atrasada que ninguém lançou.
    @Test
    void materialize_shouldNotCreateAnything_forAMonthBeforeTheCurrentOne() {
        var created = materializer.materialize(recurringExpense, YearMonth.of(2026, 9));

        assertTrue(created.isEmpty());
        verifyNoInteractions(expenseService);
    }

    @Test
    void materializeUpcoming_shouldCreateTheCurrentAndTheNextMonth() {
        materializer.materializeUpcoming(recurringExpense);

        verify(expenseService).createFromRecurrence(commandFor(recurringExpense, LocalDate.of(2026, 10, 25)));
        verify(expenseService).createFromRecurrence(commandFor(recurringExpense, LocalDate.of(2026, 11, 25)));
        verifyNoMoreInteractions(expenseService);
    }

    @Test
    void materializeUpcoming_shouldSkipTheCurrentMonth_whenTheModelStartsNextMonth() {
        recurringExpense.setStartMonth(LocalDate.of(2026, 11, 1));

        materializer.materializeUpcoming(recurringExpense);

        verify(expenseService).createFromRecurrence(commandFor(recurringExpense, LocalDate.of(2026, 11, 25)));
        verifyNoMoreInteractions(expenseService);
    }

    // O mês anterior entra junto porque, com cartão, a compra dele pode vencer no mês pedido.
    @Test
    void materializeForUser_shouldCreateThePreviousAndTheRequestedMonthOfEveryModel() {
        var other = RecurringExpense.of("Aluguel", BigDecimal.valueOf(1500), 5, YearMonth.of(2026, 1), null);
        other.setId(UUID.randomUUID());
        other.setUser(user);
        other.setCategory(category);
        when(recurringExpenseRepository.findByUserId(user.getId()))
            .thenReturn(List.of(recurringExpense, other));

        materializer.materializeForUser(user.getId(), YearMonth.of(2027, 3));

        verify(expenseService).createFromRecurrence(commandFor(recurringExpense, LocalDate.of(2027, 2, 25)));
        verify(expenseService).createFromRecurrence(commandFor(recurringExpense, LocalDate.of(2027, 3, 25)));
        verify(expenseService).createFromRecurrence(commandFor(other, LocalDate.of(2027, 2, 5)));
        verify(expenseService).createFromRecurrence(commandFor(other, LocalDate.of(2027, 3, 5)));
        verifyNoMoreInteractions(expenseService);
    }

    @Test
    void materializeForUser_shouldNotCreateThePastMonths_whenTheSummaryIsOfThePast() {
        when(recurringExpenseRepository.findByUserId(user.getId()))
            .thenReturn(List.of(recurringExpense));

        materializer.materializeForUser(user.getId(), YearMonth.of(2026, 9));

        verify(expenseService, never()).createFromRecurrence(any());
    }

    // ---- aviso ao resumo mensal (PeriodDataChangedEvent): só quando alguma despesa foi criada ----

    @Test
    void materializeUpcoming_shouldPublishFromTheCurrentMonth_whenBothMonthsWereCreated() {
        createdOn(LocalDate.of(2026, 10, 25));
        createdOn(LocalDate.of(2026, 11, 25));

        materializer.materializeUpcoming(recurringExpense);

        verify(eventPublisher, times(1)).publishEvent(any(PeriodDataChangedEvent.class));
        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(user.getId(), CURRENT_MONTH));
    }

    // O mês corrente já tinha a despesa (idempotência): só o seguinte foi criado, e o aviso parte dele.
    @Test
    void materializeUpcoming_shouldPublishFromTheNextMonth_whenOnlyTheNextOneWasCreated() {
        when(expenseService.createFromRecurrence(commandFor(recurringExpense, LocalDate.of(2026, 10, 25))))
            .thenReturn(Optional.empty());
        createdOn(LocalDate.of(2026, 11, 25));

        materializer.materializeUpcoming(recurringExpense);

        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(user.getId(), YearMonth.of(2026, 11)));
    }

    // Com cartão a despesa do mês corrente vence pela fatura, já no mês seguinte: o resumo afetado é o do vencimento real.
    @Test
    void materializeUpcoming_shouldPublishTheMonthOfTheRealDueDate_whenTheInvoiceFallsInTheNextMonth() {
        recurringExpense.setCardAccount(CardAccount.builder()
            .cardName("Nubank")
            .closingDate(3)
            .dueDate(10)
            .build());
        createdOn(CURRENT_MONTH, LocalDate.of(2026, 11, 10));
        createdOn(CURRENT_MONTH.plusMonths(1), LocalDate.of(2026, 12, 10));

        materializer.materializeUpcoming(recurringExpense);

        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(user.getId(), YearMonth.of(2026, 11)));
    }

    // Rodar de novo (ou o job repetido) não cria nada e, portanto, não manda recalcular resumo nenhum.
    @Test
    void materializeUpcoming_shouldNotPublish_whenNothingWasCreated() {
        materializer.materializeUpcoming(recurringExpense);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void materialize_shouldNotPublish_evenWhenItCreatesTheExpense() {
        createdOn(LocalDate.of(2026, 10, 25));

        materializer.materialize(recurringExpense, CURRENT_MONTH);

        verifyNoInteractions(eventPublisher);
    }

    // Quem chama é o próprio cálculo do resumo: avisar de lá seria recursão.
    @Test
    void materializeForUser_shouldNotPublish_evenWhenItCreatesExpenses() {
        when(recurringExpenseRepository.findByUserId(user.getId()))
            .thenReturn(List.of(recurringExpense));
        createdOn(LocalDate.of(2026, 10, 25));
        createdOn(LocalDate.of(2026, 11, 25));

        materializer.materializeForUser(user.getId(), YearMonth.of(2026, 11));

        verifyNoInteractions(eventPublisher);
    }

    // ---- vencimento já passado: a assinatura vale a partir do próximo vencimento, não nasce atrasada (hoje = 21/10) ----

    @Test
    void materialize_shouldNotCreateAnything_whenTheDueDateAlreadyPassedThisMonth() {
        recurringExpense.setDayOfMonth(5);

        var created = materializer.materialize(recurringExpense, CURRENT_MONTH);

        assertTrue(created.isEmpty());
        verifyNoInteractions(expenseService, recurringExpenseSkipRepository);
    }

    @Test
    void materialize_shouldNotCreateAnything_whenTheDueDateWasYesterday() {
        recurringExpense.setDayOfMonth(20);

        assertTrue(materializer.materialize(recurringExpense, CURRENT_MONTH).isEmpty());
        verifyNoInteractions(expenseService);
    }

    // O vencimento de hoje ainda não venceu: nasce normalmente.
    @Test
    void materialize_shouldCreate_whenTheDueDateIsToday() {
        recurringExpense.setDayOfMonth(21);
        createdOn(LocalDate.of(2026, 10, 21));

        var created = materializer.materialize(recurringExpense, CURRENT_MONTH);

        assertTrue(created.isPresent());
    }

    // Com cartão vale o vencimento da fatura: cobrada dia 5, antes do fechamento (dia 9), cai na fatura que venceu dia 15.
    @Test
    void materialize_shouldNotCreateAnything_whenTheInvoiceDueDateAlreadyPassed() {
        recurringExpense.setDayOfMonth(5);
        recurringExpense.setCardAccount(CardAccount.builder()
            .cardName("Nubank")
            .closingDate(9)
            .dueDate(15)
            .build());

        assertTrue(materializer.materialize(recurringExpense, CURRENT_MONTH).isEmpty());
        verifyNoInteractions(expenseService);
    }

    @Test
    void materializeUpcoming_shouldCreateOnlyTheNextMonth_whenTheDueDateOfTheCurrentOneAlreadyPassed() {
        recurringExpense.setDayOfMonth(5);
        createdOn(LocalDate.of(2026, 11, 5));

        materializer.materializeUpcoming(recurringExpense);

        verify(expenseService, never()).createFromRecurrence(commandFor(recurringExpense, LocalDate.of(2026, 10, 5)));
        verify(expenseService).createFromRecurrence(commandFor(recurringExpense, LocalDate.of(2026, 11, 5)));
        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(user.getId(), YearMonth.of(2026, 11)));
    }

    @Test
    void materializeForUser_shouldNotCreateTheExpenseThatAlreadyPassedDue() {
        recurringExpense.setDayOfMonth(5);
        when(recurringExpenseRepository.findByUserId(user.getId()))
            .thenReturn(List.of(recurringExpense));
        createdOn(LocalDate.of(2026, 11, 5));

        materializer.materializeForUser(user.getId(), YearMonth.of(2026, 11));

        verify(expenseService, never()).createFromRecurrence(commandFor(recurringExpense, LocalDate.of(2026, 10, 5)));
        verify(expenseService).createFromRecurrence(commandFor(recurringExpense, LocalDate.of(2026, 11, 5)));
    }

    // ---- geração concorrente: o lock na linha do modelo vem antes de conferir o pulo e de criar ----

    @Test
    void materialize_shouldLockTheModelBeforeCheckingTheSkipAndCreating() {
        materializer.materialize(recurringExpense, CURRENT_MONTH);

        var inOrder = inOrder(recurringExpenseRepository, recurringExpenseSkipRepository, expenseService);
        inOrder.verify(recurringExpenseRepository).findByIdForUpdate(recurringExpense.getId());
        inOrder.verify(recurringExpenseSkipRepository).existsByRecurringExpenseIdAndDueDate(recurringExpense.getId(), LocalDate.of(2026, 10, 25));
        inOrder.verify(expenseService).createFromRecurrence(commandFor(recurringExpense, LocalDate.of(2026, 10, 25)));
    }

    // Sem nada para criar não há por que travar a linha do modelo.
    @Test
    void materialize_shouldNotLockTheModel_whenThereIsNothingToCreate() {
        recurringExpense.setStartMonth(LocalDate.of(2026, 11, 1));
        materializer.materialize(recurringExpense, CURRENT_MONTH);
        materializer.materialize(recurringExpense, YearMonth.of(2026, 9));
        recurringExpense.setStartMonth(LocalDate.of(2026, 8, 1));
        recurringExpense.setDayOfMonth(5);
        materializer.materialize(recurringExpense, CURRENT_MONTH);

        verifyNoInteractions(recurringExpenseRepository);
    }

    // ---- mês pulado: o usuário apagou a despesa deste vencimento de propósito, e ela não volta ----

    @Test
    void materialize_shouldNotCreateAnything_whenTheDueDateWasSkipped() {
        skipped(LocalDate.of(2026, 10, 25));

        var created = materializer.materialize(recurringExpense, CURRENT_MONTH);

        assertTrue(created.isEmpty());
        verifyNoInteractions(expenseService);
    }

    // Com cartão o vencimento segue a fatura: o pulo é conferido na data real do vencimento, não no dia do modelo.
    @Test
    void materialize_shouldCheckTheSkipOnTheInvoiceDueDate_whenThereIsACard() {
        recurringExpense.setCardAccount(CardAccount.builder()
            .cardName("Nubank")
            .closingDate(3)
            .dueDate(10)
            .build());
        skipped(LocalDate.of(2026, 11, 10));

        var created = materializer.materialize(recurringExpense, CURRENT_MONTH);

        assertTrue(created.isEmpty());
        verifyNoInteractions(expenseService);
    }

    @Test
    void materialize_shouldStillCreateTheOtherMonths_whenOnlyOneDueDateWasSkipped() {
        skipped(LocalDate.of(2026, 10, 25));

        materializer.materializeUpcoming(recurringExpense);

        verify(expenseService, never()).createFromRecurrence(commandFor(recurringExpense, LocalDate.of(2026, 10, 25)));
        verify(expenseService).createFromRecurrence(commandFor(recurringExpense, LocalDate.of(2026, 11, 25)));
    }

    // O mês corrente foi pulado e o seguinte é criado: o aviso ao resumo parte do mês do que foi de fato criado.
    @Test
    void materializeUpcoming_shouldPublishFromTheNextMonth_whenTheCurrentOneWasSkipped() {
        skipped(LocalDate.of(2026, 10, 25));
        createdOn(LocalDate.of(2026, 11, 25));

        materializer.materializeUpcoming(recurringExpense);

        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(user.getId(), YearMonth.of(2026, 11)));
    }

    @Test
    void materializeUpcoming_shouldNotPublish_whenBothMonthsWereSkipped() {
        skipped(LocalDate.of(2026, 10, 25));
        skipped(LocalDate.of(2026, 11, 25));

        materializer.materializeUpcoming(recurringExpense);

        verifyNoInteractions(expenseService, eventPublisher);
    }

    @Test
    void materializeForUser_shouldNotRecreateTheSkippedMonth() {
        when(recurringExpenseRepository.findByUserId(user.getId()))
            .thenReturn(List.of(recurringExpense));
        when(recurringExpenseSkipRepository.existsByRecurringExpenseIdAndDueDate(recurringExpense.getId(), LocalDate.of(2026, 10, 25)))
            .thenReturn(false);
        skipped(LocalDate.of(2026, 11, 25));

        materializer.materializeForUser(user.getId(), YearMonth.of(2026, 11));

        verify(expenseService, never()).createFromRecurrence(commandFor(recurringExpense, LocalDate.of(2026, 11, 25)));
    }

    private void skipped(LocalDate dueDate) {
        when(recurringExpenseSkipRepository.existsByRecurringExpenseIdAndDueDate(recurringExpense.getId(), dueDate))
            .thenReturn(true);
    }

    // O VO que o materializer entrega ao ExpenseService, campo a campo: o expense não conhece o modelo, só estes dados.
    private RecurrenceExpenseCommand commandFor(RecurringExpense model, LocalDate dueDate) {
        return commandFor(model, YearMonth.from(dueDate), dueDate);
    }

    private RecurrenceExpenseCommand commandFor(RecurringExpense model, YearMonth month, LocalDate dueDate) {
        return new RecurrenceExpenseCommand(model.getId(), month, model.getDescription(), model.getValue(), dueDate, model.getUser(),
            model.getCategory(), model.getCardAccount());
    }

    private void createdOn(LocalDate dueDate) {
        createdOn(YearMonth.from(dueDate), dueDate);
    }

    private void createdOn(YearMonth month, LocalDate dueDate) {
        when(expenseService.createFromRecurrence(commandFor(recurringExpense, month, dueDate)))
            .thenReturn(Optional.of(Expense.builder().dueDate(dueDate).build()));
    }

}
