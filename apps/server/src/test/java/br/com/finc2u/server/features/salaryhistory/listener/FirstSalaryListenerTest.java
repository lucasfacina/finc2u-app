package br.com.finc2u.server.features.salaryhistory.listener;

import br.com.finc2u.server.features.salaryhistory.entity.SalaryHistory;
import br.com.finc2u.server.features.salaryhistory.service.SalaryHistoryService;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.event.UserRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirstSalaryListenerTest {

    @Mock
    private SalaryHistoryService salaryHistoryService;

    @InjectMocks
    private FirstSalaryListener firstSalaryListener;

    @Test
    void onUserRegistered_shouldCreateFirstSalaryForTheRegisteredUser() {
        var user = User.builder()
            .name("Ana")
            .email("ana@email.com")
            .build();
        user.setId(UUID.randomUUID());
        var event = new UserRegisteredEvent(user, BigDecimal.valueOf(3000), LocalDate.of(2026, 10, 1));

        firstSalaryListener.onUserRegistered(event);

        var captor = ArgumentCaptor.forClass(SalaryHistory.class);
        verify(salaryHistoryService).create(captor.capture(), eq(user.getId()));
        assertEquals(0, BigDecimal.valueOf(3000).compareTo(captor.getValue().getBaseSalary()));
        assertEquals(LocalDate.of(2026, 10, 1), captor.getValue().getEffectiveFrom());
    }

    @Test
    void onUserRegistered_shouldPropagateFailure_soTheRegistrationIsRolledBack() {
        var user = User.builder()
            .build();
        user.setId(UUID.randomUUID());
        var event = new UserRegisteredEvent(user, BigDecimal.valueOf(3000), LocalDate.of(2026, 10, 1));
        when(salaryHistoryService.create(any(SalaryHistory.class), eq(user.getId())))
            .thenThrow(new IllegalStateException("falha ao gravar salário"));

        assertThrows(IllegalStateException.class, () -> firstSalaryListener.onUserRegistered(event));
    }

}
