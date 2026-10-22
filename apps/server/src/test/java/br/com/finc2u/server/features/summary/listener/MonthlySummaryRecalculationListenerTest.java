package br.com.finc2u.server.features.summary.listener;

import br.com.finc2u.server.features.summary.service.MonthlySummaryService;
import br.com.finc2u.server.shared.event.PeriodDataChangedEvent;
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
class MonthlySummaryRecalculationListenerTest {

    @Mock
    private MonthlySummaryService monthlySummaryService;

    @InjectMocks
    private MonthlySummaryRecalculationListener listener;

    @Test
    void onPeriodDataChanged_shouldRecalculateTheExistingSummariesFromTheMonthOfTheEvent() {
        var userId = UUID.randomUUID();

        listener.onPeriodDataChanged(new PeriodDataChangedEvent(userId, YearMonth.of(2027, 3)));

        verify(monthlySummaryService).recalculateExistingFrom(userId, YearMonth.of(2027, 3));
    }

    @Test
    void onPeriodDataChanged_shouldPropagateFailure_soTheChangeIsRolledBack() {
        var userId = UUID.randomUUID();
        var from = YearMonth.of(2027, 3);
        doThrow(new IllegalStateException("falha no recálculo")).when(monthlySummaryService).recalculateExistingFrom(userId, from);

        assertThrows(IllegalStateException.class,
            () -> listener.onPeriodDataChanged(new PeriodDataChangedEvent(userId, from)));
    }

}
