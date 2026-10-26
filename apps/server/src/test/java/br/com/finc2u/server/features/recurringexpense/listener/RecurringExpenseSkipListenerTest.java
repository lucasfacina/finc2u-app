package br.com.finc2u.server.features.recurringexpense.listener;

import br.com.finc2u.server.features.expense.event.GeneratedExpenseDeletedEvent;
import br.com.finc2u.server.features.recurringexpense.service.RecurringExpenseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecurringExpenseSkipListenerTest {

    @Mock
    private RecurringExpenseService recurringExpenseService;

    @InjectMocks
    private RecurringExpenseSkipListener listener;

    @Test
    void onGeneratedExpenseDeleted_shouldSkipTheMonthOfTheModel() {
        var modelId = UUID.randomUUID();
        var month = YearMonth.of(2026, 10);

        listener.onGeneratedExpenseDeleted(new GeneratedExpenseDeletedEvent(modelId, month));

        verify(recurringExpenseService).skipMonth(modelId, month);
    }

    @Test
    void onGeneratedExpenseDeleted_shouldPropagateFailure_soTheDeletionIsRolledBack() {
        var modelId = UUID.randomUUID();
        var month = YearMonth.of(2026, 10);
        doThrow(new IllegalStateException("falha ao registrar o pulo")).when(recurringExpenseService).skipMonth(modelId, month);

        assertThrows(IllegalStateException.class,
            () -> listener.onGeneratedExpenseDeleted(new GeneratedExpenseDeletedEvent(modelId, month)));
    }

}
