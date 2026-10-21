package br.com.finc2u.server.features.extraincome.scripts.seeders;

import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import br.com.finc2u.server.features.extraincome.repository.ExtraIncomeRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExtraIncomeSeederTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @Mock
    private ExtraIncomeRepository extraIncomeRepository;

    @Captor
    private ArgumentCaptor<List<ExtraIncome>> extraIncomesCaptor;

    private SeedContext context;

    @BeforeEach
    void setUp() {
        var user = User.builder()
            .name("Ana")
            .build();
        user.setId(UUID.randomUUID());
        context = new SeedContext();
        context.setUser(user);
    }

    // Relativo ao mês do Clock: o resumo de hoje sempre tem ganhos extras.
    @Test
    void seed_shouldDateTheExtraIncomesInTheMonthOfTheClock() {
        seederAt(TODAY).seed(context);

        verify(extraIncomeRepository).saveAll(extraIncomesCaptor.capture());
        var extraIncomes = extraIncomesCaptor.getValue();
        assertEquals(2, extraIncomes.size());
        assertTrue(extraIncomes.stream().allMatch(extraIncome -> LocalDate.of(2026, 10, 15).equals(extraIncome.getDate())));
    }

    @Test
    void seed_shouldFollowTheClock_whenTheMonthChanges() {
        seederAt(LocalDate.of(2027, 3, 5)).seed(context);

        verify(extraIncomeRepository).saveAll(extraIncomesCaptor.capture());
        assertTrue(extraIncomesCaptor.getValue().stream()
            .allMatch(extraIncome -> LocalDate.of(2027, 3, 15).equals(extraIncome.getDate())));
    }

    @Test
    void seed_shouldNotSaveAnything_whenTheUserAlreadyHasExtraIncomes() {
        when(extraIncomeRepository.existsByUserId(context.getUser().getId()))
            .thenReturn(true);

        seederAt(TODAY).seed(context);

        verify(extraIncomeRepository, never()).saveAll(anyList());
    }

    private ExtraIncomeSeeder seederAt(LocalDate today) {
        return new ExtraIncomeSeeder(extraIncomeRepository, Clock.fixed(today.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
    }

}
