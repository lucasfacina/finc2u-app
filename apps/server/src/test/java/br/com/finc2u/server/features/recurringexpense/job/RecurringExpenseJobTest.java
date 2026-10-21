package br.com.finc2u.server.features.recurringexpense.job;

import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.materializer.RecurringExpenseMaterializer;
import br.com.finc2u.server.features.recurringexpense.service.RecurringExpenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringExpenseJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    @Mock
    private RecurringExpenseService recurringExpenseService;

    @Mock
    private RecurringExpenseMaterializer recurringExpenseMaterializer;

    @Spy
    @SuppressWarnings("unused") // injetado no job pelo @InjectMocks
    private Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @InjectMocks
    private RecurringExpenseJob recurringExpenseJob;

    private RecurringExpense spotify;
    private RecurringExpense rent;

    @BeforeEach
    void setUp() {
        spotify = modelOf("Spotify");
        rent = modelOf("Aluguel");
    }

    // O job roda sem esperar o cron: o teste chama o metodo direto, e o mês vem do Clock.
    @Test
    void materializeActiveRecurringExpenses_shouldMaterializeEachActiveModelOfTheCurrentMonth() {
        when(recurringExpenseService.getActiveIn(YearMonth.of(2026, 11)))
            .thenReturn(List.of(spotify, rent));

        recurringExpenseJob.materializeActiveRecurringExpenses();

        verify(recurringExpenseMaterializer).materializeUpcoming(spotify);
        verify(recurringExpenseMaterializer).materializeUpcoming(rent);
        verifyNoMoreInteractions(recurringExpenseMaterializer);
    }

    // Os encerrados não chegam ao job: a consulta de modelos ativos já os deixa de fora (RecurringExpenseRepositoryTest).
    @Test
    void materializeActiveRecurringExpenses_shouldNotCallTheMaterializer_whenThereIsNoActiveModel() {
        when(recurringExpenseService.getActiveIn(YearMonth.of(2026, 11)))
            .thenReturn(List.of());

        recurringExpenseJob.materializeActiveRecurringExpenses();

        verifyNoInteractions(recurringExpenseMaterializer);
    }

    @Test
    void materializeActiveRecurringExpenses_shouldKeepGoing_whenOneModelFails() {
        when(recurringExpenseService.getActiveIn(YearMonth.of(2026, 11)))
            .thenReturn(List.of(spotify, rent));
        doThrow(new IllegalStateException("falha ao gerar"))
            .when(recurringExpenseMaterializer).materializeUpcoming(spotify);

        recurringExpenseJob.materializeActiveRecurringExpenses();

        verify(recurringExpenseMaterializer).materializeUpcoming(rent);
    }

    private RecurringExpense modelOf(String description) {
        var model = RecurringExpense.of(description, BigDecimal.TEN, 5, YearMonth.of(2026, 1), null);
        model.setId(UUID.randomUUID());
        return model;
    }

}
