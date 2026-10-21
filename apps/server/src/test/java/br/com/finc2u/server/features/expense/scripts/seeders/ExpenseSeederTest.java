package br.com.finc2u.server.features.expense.scripts.seeders;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.shared.seed.SeedContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseSeederTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @Mock
    private ExpenseRepository expenseRepository;

    @Captor
    private ArgumentCaptor<List<Expense>> expensesCaptor;

    private SeedContext context;

    @BeforeEach
    void setUp() {
        var user = User.builder()
            .name("Ana")
            .build();
        user.setId(UUID.randomUUID());
        context = new SeedContext();
        context.setUser(user);
        context.putCard(CardAccount.builder().cardName("Nubank").build());
        context.putCard(CardAccount.builder().cardName("Mercado Pago").build());
        List.of("Assinaturas", "Alimentação", "Lazer", "Saúde", "Transporte", "Moradia")
            .forEach(name -> context.putCategory(Category.of(name)));
    }

    @Test
    void seed_shouldNotSaveAnything_whenTheUserAlreadyHasExpenses() {
        when(expenseRepository.existsByUserId(context.getUser().getId()))
            .thenReturn(true);

        seederAt(TODAY).seed(context);

        verify(expenseRepository, never()).saveAll(anyList());
    }

    // O mês corrente sempre tem dados: os vencimentos saem do mês do Clock, não de uma data fixa.
    @Test
    void seed_shouldBuildTheDueDatesFromTheMonthOfTheClock() {
        var expenses = seedAt(TODAY);

        assertEquals(LocalDate.of(2026, 10, 15), byDescription(expenses, "iCloud").getDueDate());
        assertEquals(LocalDate.of(2026, 10, 15), byDescription(expenses, "Mercado").getDueDate());
        assertEquals(LocalDate.of(2026, 10, 14), byDescription(expenses, "Farmácia").getDueDate());
        assertEquals(LocalDate.of(2026, 10, 25), byDescription(expenses, "Busão").getDueDate());
    }

    @Test
    void seed_shouldFollowTheClock_whenTheMonthChanges() {
        var expenses = seedAt(LocalDate.of(2027, 3, 5));

        assertEquals(LocalDate.of(2027, 3, 15), byDescription(expenses, "iCloud").getDueDate());
        assertEquals(LocalDate.of(2027, 3, 2), byDescription(expenses, "Conta de luz").getDueDate());
    }

    // Netflix e Vivo agora são despesas recorrentes (RecurringExpenseSeeder): não podem nascer duas vezes.
    @Test
    void seed_shouldNotCreateTheExpensesThatAreRecurring() {
        var expenses = seedAt(TODAY);

        assertTrue(expenses.stream().noneMatch(expense -> Set.of("Netflix", "Vivo").contains(expense.getDescription())));
    }

    @Test
    void seed_shouldCreateTheRemainingInstallmentsOfTheOngoingPurchaseInTheSameGroup() {
        var expenses = seedAt(TODAY);

        var installments = expenses.stream()
            .filter(expense -> "Eletrônico".equals(expense.getDescription()))
            .toList();

        assertEquals(List.of(4, 5, 6, 7, 8, 9, 10), installments.stream().map(Expense::getCurrentInstallment).toList());
        assertEquals(LocalDate.of(2026, 10, 14), installments.getFirst().getDueDate());
        assertEquals(LocalDate.of(2027, 4, 14), installments.getLast().getDueDate());
        assertEquals(1, installments.stream().map(Expense::getInstallmentGroupId).distinct().count());
        assertNotNull(installments.getFirst().getInstallmentGroupId());
        assertTrue(installments.stream().allMatch(expense -> expense.getPaymentStatus() == PaymentStatus.PENDING));
    }

    @Test
    void seed_shouldKeepTheFinishedPurchaseAsASingleLineWithItsOwnGroup() {
        var expenses = seedAt(TODAY);

        var finished = expenses.stream()
            .filter(expense -> "Compra parcelada".equals(expense.getDescription()))
            .toList();

        assertEquals(1, finished.size());
        assertEquals(5, finished.getFirst().getCurrentInstallment());
        assertNotNull(finished.getFirst().getInstallmentGroupId());
    }

    // Venceu há 3 dias e continua pendente: exercita o atraso derivado em qualquer dia em que o seed rodar.
    @Test
    void seed_shouldCreateAPendingExpenseThatIsAlwaysOverdue() {
        for (var today : List.of(TODAY, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 1, 31))) {
            var overdue = byDescription(seedAt(today), "Conta de luz");

            assertEquals(PaymentStatus.PENDING, overdue.getPaymentStatus());
            assertTrue(overdue.isOverdue(today));
        }
    }

    @Test
    void seed_shouldNeverSetAFuturePaidAt() {
        var expenses = seedAt(TODAY);

        assertEquals(LocalDate.of(2026, 10, 20), byDescription(expenses, "Farmácia").getPaidAt());
        assertTrue(expenses.stream()
            .filter(expense -> expense.getPaidAt() != null)
            .noneMatch(expense -> expense.getPaidAt().isAfter(TODAY)));
    }

    @Test
    void seed_shouldCoverEveryTypeAndStatusWithAndWithoutCard() {
        var expenses = seedAt(TODAY);

        assertEquals(Set.of(ExpenseType.FIXED, ExpenseType.VARIABLE, ExpenseType.PARCELED),
            expenses.stream().map(Expense::getExpenseType).collect(Collectors.toSet()));
        assertEquals(Set.of(PaymentStatus.PAID, PaymentStatus.PENDING),
            expenses.stream().map(Expense::getPaymentStatus).collect(Collectors.toSet()));
        assertTrue(expenses.stream().anyMatch(expense -> expense.getCardAccount() == null));
        assertTrue(expenses.stream().anyMatch(expense -> expense.getCardAccount() != null));
    }

    private List<Expense> seedAt(LocalDate today) {
        clearInvocations(expenseRepository);
        seederAt(today).seed(context);
        verify(expenseRepository).saveAll(expensesCaptor.capture());
        return expensesCaptor.getValue();
    }

    private ExpenseSeeder seederAt(LocalDate today) {
        return new ExpenseSeeder(expenseRepository, Clock.fixed(today.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
    }

    private Expense byDescription(List<Expense> expenses, String description) {
        return expenses.stream()
            .filter(expense -> description.equals(expense.getDescription()))
            .findFirst()
            .orElseThrow();
    }

}
