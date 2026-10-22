package br.com.finc2u.server.features.recurringexpense.listener;

import br.com.finc2u.server.features.expense.event.GeneratedExpenseDeletedEvent;
import br.com.finc2u.server.features.recurringexpense.service.RecurringExpenseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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
    void onGeneratedExpenseDeleted_shouldSkipTheDueDateOfTheModel() {
        var modelId = UUID.randomUUID();
        var dueDate = LocalDate.of(2026, 10, 25);

        listener.onGeneratedExpenseDeleted(new GeneratedExpenseDeletedEvent(modelId, dueDate));

        verify(recurringExpenseService).skipMonth(modelId, dueDate);
    }

    @Test
    void onGeneratedExpenseDeleted_shouldPropagateFailure_soTheDeletionIsRolledBack() {
        var modelId = UUID.randomUUID();
        var dueDate = LocalDate.of(2026, 10, 25);
        doThrow(new IllegalStateException("falha ao registrar o pulo")).when(recurringExpenseService).skipMonth(modelId, dueDate);

        assertThrows(IllegalStateException.class,
            () -> listener.onGeneratedExpenseDeleted(new GeneratedExpenseDeletedEvent(modelId, dueDate)));
    }

}
