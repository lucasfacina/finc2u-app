package br.com.finc2u.server.features.recurringexpense.scripts.seeders;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.materializer.RecurringExpenseMaterializer;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.shared.seed.SeedContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringExpenseSeederTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @Mock
    private RecurringExpenseRepository recurringExpenseRepository;

    @Mock
    private RecurringExpenseMaterializer recurringExpenseMaterializer;

    @Spy
    @SuppressWarnings("unused") // injetado no seeder pelo construtor
    private Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @Captor
    private ArgumentCaptor<List<RecurringExpense>> modelsCaptor;

    private RecurringExpenseSeeder seeder;
    private SeedContext context;
    private CardAccount nubank;
    private CardAccount mercadoPago;

    @BeforeEach
    void setUp() {
        var user = User.builder()
            .name("Ana")
            .build();
        user.setId(UUID.randomUUID());
        nubank = CardAccount.builder().cardName("Nubank").build();
        mercadoPago = CardAccount.builder().cardName("Mercado Pago").build();
        context = new SeedContext();
        context.put(User.class, user);
        context.put(CardAccount.class, nubank.getCardName(), nubank);
        context.put(CardAccount.class, mercadoPago.getCardName(), mercadoPago);
        List.of("Assinaturas", "Saúde").forEach(name -> context.put(Category.class, name, Category.of(name)));
        seeder = new RecurringExpenseSeeder(recurringExpenseRepository, recurringExpenseMaterializer, clock);
    }

    @Test
    void seed_shouldCreateTheModelsStartingInTheMonthOfTheClock() {
        when(recurringExpenseRepository.findByUserIdOrderByDescriptionAsc(context.get(User.class).getId()))
            .thenReturn(List.of());

        seeder.seed(context);

        verify(recurringExpenseRepository).saveAll(modelsCaptor.capture());
        var models = modelsCaptor.getValue();
        assertEquals(List.of("Netflix", "Vivo", "Gympass"), models.stream().map(RecurringExpense::getDescription).toList());
        assertTrue(models.stream().allMatch(model -> LocalDate.of(2026, 10, 1).equals(model.getStartMonth())));
        assertTrue(models.stream().allMatch(model -> model.getUser() == context.get(User.class)));
    }

    @Test
    void seed_shouldCoverWithAndWithoutCardAndWithAndWithoutEndMonth() {
        when(recurringExpenseRepository.findByUserIdOrderByDescriptionAsc(context.get(User.class).getId()))
            .thenReturn(List.of());

        seeder.seed(context);

        verify(recurringExpenseRepository).saveAll(modelsCaptor.capture());
        var models = modelsCaptor.getValue();
        assertSame(nubank, models.get(0).getCardAccount());
        assertNull(models.get(1).getCardAccount());
        assertSame(mercadoPago, models.get(2).getCardAccount());
        assertNull(models.get(0).getEndMonth());
        assertEquals(LocalDate.of(2027, 3, 1), models.get(2).getEndMonth());
    }

    @Test
    void seed_shouldMaterializeTheUpcomingMonthsOfEveryModel() {
        when(recurringExpenseRepository.findByUserIdOrderByDescriptionAsc(context.get(User.class).getId()))
            .thenReturn(List.of());

        seeder.seed(context);

        verify(recurringExpenseMaterializer, times(3)).materializeUpcoming(any(RecurringExpense.class));
    }

    // Idempotente por descrição, mas gera de novo para todos: num mês novo, o banco já populado ganha as despesas do mês.
    @Test
    void seed_shouldNotRecreateAnExistingModel_butMaterializeItAgain() {
        var existing = RecurringExpense.of("Netflix", new BigDecimal("20.90"), 15, YearMonth.of(2026, 9), null);
        when(recurringExpenseRepository.findByUserIdOrderByDescriptionAsc(context.get(User.class).getId()))
            .thenReturn(List.of(existing));

        seeder.seed(context);

        verify(recurringExpenseRepository).saveAll(modelsCaptor.capture());
        assertEquals(List.of("Vivo", "Gympass"), modelsCaptor.getValue().stream().map(RecurringExpense::getDescription).toList());
        verify(recurringExpenseMaterializer).materializeUpcoming(existing);
        verify(recurringExpenseMaterializer, times(3)).materializeUpcoming(any(RecurringExpense.class));
    }

}
