package br.com.finc2u.server.features.extraincome.service;

import br.com.finc2u.server.exception.InvalidRequestException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import br.com.finc2u.server.features.extraincome.repository.ExtraIncomeRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import br.com.finc2u.server.shared.event.PeriodDataChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExtraIncomeServiceTest {

    @Mock
    private ExtraIncomeRepository extraIncomeRepository;

    @Mock
    private UserService userService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ExtraIncomeService extraIncomeService;

    private UUID extraIncomeId;
    private UUID userId;
    private User user;
    private ExtraIncome extraIncome;

    @BeforeEach
    void setUp() {
        extraIncomeId = UUID.randomUUID();
        userId = UUID.randomUUID();

        user = User.builder()
            .build();
        user.setId(userId);

        extraIncome = ExtraIncome.builder()
            .description("Freela site")
            .value(BigDecimal.valueOf(800))
            .date(LocalDate.of(2026, 5, 10))
            .user(user)
            .build();
        extraIncome.setId(extraIncomeId);
    }

    @Test
    void create_shouldAssociateUserAndSave() {
        when(userService.getById(userId))
            .thenReturn(user);
        when(extraIncomeRepository.save(any(ExtraIncome.class)))
            .thenReturn(extraIncome);

        var result = extraIncomeService.create(extraIncome, userId);

        assertNotNull(result);
        assertEquals(user, result.getUser());
        verify(extraIncomeRepository, times(1)).save(extraIncome);
    }

    @Test
    void getById_shouldReturnExtra_whenExists() {
        when(extraIncomeRepository.findById(extraIncomeId))
            .thenReturn(Optional.of(extraIncome));

        var result = extraIncomeService.getById(extraIncomeId);

        assertNotNull(result);
        assertEquals(extraIncomeId, result.getId());
    }

    @Test
    void getById_shouldThrow_whenNotFound() {
        when(extraIncomeRepository.findById(extraIncomeId))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> extraIncomeService.getById(extraIncomeId));
    }

    @Test
    void getByUser_shouldReturnList() {
        when(userService.getById(userId))
            .thenReturn(user);
        when(extraIncomeRepository.findByUserId(userId))
            .thenReturn(List.of(extraIncome));

        var result = extraIncomeService.getByUser(userId);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void update_shouldUpdateFieldsAndSave() {
        var updates = ExtraIncome.builder()
            .description("Freela app")
            .value(BigDecimal.valueOf(1200))
            .date(LocalDate.of(2026, 5, 20))
            .build();

        when(extraIncomeRepository.findById(extraIncomeId))
            .thenReturn(Optional.of(extraIncome));
        when(extraIncomeRepository.save(any(ExtraIncome.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        var result = extraIncomeService.update(extraIncomeId, updates);

        assertEquals("Freela app", result.getDescription());
        assertEquals(0, BigDecimal.valueOf(1200).compareTo(result.getValue()));
        assertEquals(LocalDate.of(2026, 5, 20), result.getDate());
        verify(extraIncomeRepository, times(1)).save(extraIncome);
    }

    @Test
    void getByUserAndPeriod_shouldReturnTheIncomesOfThePeriod() {
        var start = LocalDate.of(2026, 5, 1);
        var end = LocalDate.of(2026, 5, 31);
        when(userService.getById(userId))
            .thenReturn(user);
        when(extraIncomeRepository.findByUserIdAndDateBetween(userId, start, end))
            .thenReturn(List.of(extraIncome));

        var result = extraIncomeService.getByUserAndPeriod(userId, start, end);

        assertEquals(List.of(extraIncome), result);
    }

    @Test
    void getByUserAndPeriod_shouldAcceptASingleDayPeriod() {
        var day = LocalDate.of(2026, 5, 10);
        when(userService.getById(userId))
            .thenReturn(user);
        when(extraIncomeRepository.findByUserIdAndDateBetween(userId, day, day))
            .thenReturn(List.of(extraIncome));

        assertEquals(1, extraIncomeService.getByUserAndPeriod(userId, day, day).size());
    }

    @Test
    void getByUserAndPeriod_shouldThrowInvalidRequest_whenStartIsAfterEnd() {
        var exception = assertThrows(InvalidRequestException.class, () -> extraIncomeService.getByUserAndPeriod(
            userId, LocalDate.of(2026, 5, 31), LocalDate.of(2026, 5, 1)));

        assertEquals("A data inicial não pode ser posterior à data final.", exception.getMessage());
        verifyNoInteractions(userService, extraIncomeRepository);
    }

    @Test
    void delete_shouldCallRepositoryDelete() {
        when(extraIncomeRepository.findById(extraIncomeId))
            .thenReturn(Optional.of(extraIncome));
        doNothing().when(extraIncomeRepository).delete(extraIncome);

        extraIncomeService.delete(extraIncomeId);

        verify(extraIncomeRepository, times(1)).delete(extraIncome);
    }

    @Test
    void delete_shouldThrow_whenNotFound() {
        when(extraIncomeRepository.findById(extraIncomeId))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> extraIncomeService.delete(extraIncomeId));
    }

    // ---- aviso ao resumo mensal (PeriodDataChangedEvent): o mês mais antigo afetado ----

    @Test
    void create_shouldPublishThePeriodOfTheDate() {
        when(userService.getById(userId))
            .thenReturn(user);
        when(extraIncomeRepository.save(any(ExtraIncome.class)))
            .thenReturn(extraIncome);

        extraIncomeService.create(extraIncome, userId);

        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(2026, 5)));
    }

    @Test
    void create_shouldNotPublish_whenTheUserDoesNotExist() {
        when(userService.getById(userId))
            .thenThrow(new ResourceNotFoundException("Usuário não encontrado"));

        assertThrows(ResourceNotFoundException.class, () -> extraIncomeService.create(extraIncome, userId));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void update_shouldPublishTheOlderMonth_whenTheDateMovesToAnEarlierMonth() {
        updateMovingTheDate(LocalDate.of(2026, 3, 20));

        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(2026, 3)));
    }

    @Test
    void update_shouldPublishTheOlderMonth_whenTheDateMovesToALaterMonth() {
        updateMovingTheDate(LocalDate.of(2026, 8, 1));

        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(2026, 5)));
    }

    @Test
    void update_shouldNotPublish_whenTheIncomeDoesNotExist() {
        when(extraIncomeRepository.findById(extraIncomeId))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> extraIncomeService.update(extraIncomeId, extraIncome));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void delete_shouldPublishThePeriodOfTheDate() {
        when(extraIncomeRepository.findById(extraIncomeId))
            .thenReturn(Optional.of(extraIncome));

        extraIncomeService.delete(extraIncomeId);

        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(2026, 5)));
    }

    @Test
    void delete_shouldNotPublish_whenTheIncomeDoesNotExist() {
        when(extraIncomeRepository.findById(extraIncomeId))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> extraIncomeService.delete(extraIncomeId));

        verifyNoInteractions(eventPublisher);
    }

    private void updateMovingTheDate(LocalDate newDate) {
        var updates = ExtraIncome.builder()
            .description("Freela site")
            .value(BigDecimal.valueOf(800))
            .date(newDate)
            .build();
        when(extraIncomeRepository.findById(extraIncomeId))
            .thenReturn(Optional.of(extraIncome));
        when(extraIncomeRepository.save(any(ExtraIncome.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        extraIncomeService.update(extraIncomeId, updates);
    }

}
