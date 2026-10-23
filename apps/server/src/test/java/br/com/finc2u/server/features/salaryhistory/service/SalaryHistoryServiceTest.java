package br.com.finc2u.server.features.salaryhistory.service;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.salaryhistory.entity.SalaryHistory;
import br.com.finc2u.server.features.salaryhistory.repository.SalaryHistoryRepository;
import br.com.finc2u.server.features.salaryhistory.vo.CurrentSalary;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalaryHistoryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @Mock
    private SalaryHistoryRepository salaryHistoryRepository;
    @Mock
    private UserService userService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    @SuppressWarnings("unused") // injetado no service pelo @InjectMocks
    private Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @InjectMocks
    private SalaryHistoryService salaryHistoryService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
            .build();
        user.setId(userId);
    }

    @Test
    void create_shouldSaveAndReturnHistory() {
        var input = SalaryHistory.builder()
            .baseSalary(BigDecimal.valueOf(3500))
            .effectiveFrom(LocalDate.of(2026, 8, 1))
            .build();

        var saved = SalaryHistory.builder()
            .user(user)
            .baseSalary(BigDecimal.valueOf(3500))
            .effectiveFrom(LocalDate.of(2026, 8, 1))
            .build();

        when(userService.getById(userId)).thenReturn(user);
        when(salaryHistoryRepository.save(any())).thenReturn(saved);

        var result = salaryHistoryService.create(input, userId);

        assertEquals(0, BigDecimal.valueOf(3500).compareTo(result.getBaseSalary()));
        assertEquals(LocalDate.of(2026, 8, 1), result.getEffectiveFrom());
        verify(salaryHistoryRepository).save(any(SalaryHistory.class));
    }

    @Test
    void getByUser_shouldReturnHistoryOrderedByDateDesc() {
        var history1 = SalaryHistory.builder()
            .user(user)
            .baseSalary(BigDecimal.valueOf(3500))
            .effectiveFrom(LocalDate.of(2026, 8, 1))
            .build();
        var history2 = SalaryHistory.builder()
            .user(user)
            .baseSalary(BigDecimal.valueOf(3000))
            .effectiveFrom(LocalDate.of(2026, 1, 1))
            .build();

        when(salaryHistoryRepository.findByUserIdOrderByEffectiveFromDesc(userId)).thenReturn(List.of(history1, history2));

        var result = salaryHistoryService.getByUser(userId);

        assertEquals(2, result.size());
        assertEquals(0, BigDecimal.valueOf(3500).compareTo(result.getFirst().getBaseSalary()));
    }

    @Test
    void getBaseSalaryForPeriod_shouldReturnHistoricalSalary_whenExists() {
        var history = SalaryHistory.builder()
            .user(user)
            .baseSalary(BigDecimal.valueOf(3000))
            .effectiveFrom(LocalDate.of(2026, 1, 1))
            .build();

        when(salaryHistoryRepository.findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            userId, LocalDate.of(2026, 6, 1)))
            .thenReturn(Optional.of(history));

        var result = salaryHistoryService.getBaseSalaryForPeriod(
            userId,
            LocalDate.of(2026, 6, 1)
        );

        assertEquals(0, BigDecimal.valueOf(3000).compareTo(result));
    }

    @Test
    void getBaseSalaryForPeriod_shouldReturnZero_whenPeriodIsBeforeTheFirstSalary() {
        when(salaryHistoryRepository.findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            userId, LocalDate.of(2026, 6, 1)))
            .thenReturn(Optional.empty());

        var result = salaryHistoryService.getBaseSalaryForPeriod(
            userId,
            LocalDate.of(2026, 6, 1)
        );

        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }

    @Test
    void getBaseSalaryForPeriod_shouldNotReturnFutureSalary() {
        var januarySalary = SalaryHistory.builder()
            .user(user)
            .baseSalary(BigDecimal.valueOf(3000))
            .effectiveFrom(LocalDate.of(2026, 1, 1))
            .build();

        when(salaryHistoryRepository.findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            userId, LocalDate.of(2026, 6, 1)))
            .thenReturn(Optional.of(januarySalary));

        var result = salaryHistoryService.getBaseSalaryForPeriod(
            userId,
            LocalDate.of(2026, 6, 1)
        );

        assertEquals(0, BigDecimal.valueOf(3000).compareTo(result));
    }

    @Test
    void getCurrentBaseSalaries_shouldMapEachUserToTheCurrentSalary_inASingleQuery() {
        var otherUserId = UUID.randomUUID();
        var userIds = List.of(userId, otherUserId);
        when(salaryHistoryRepository.findCurrentSalaries(userIds, TODAY))
            .thenReturn(List.of(
                new CurrentSalary(userId, BigDecimal.valueOf(3500)),
                new CurrentSalary(otherUserId, BigDecimal.valueOf(4200))));

        var result = salaryHistoryService.getCurrentBaseSalaries(userIds);

        assertEquals(2, result.size());
        assertEquals(0, BigDecimal.valueOf(3500).compareTo(result.get(userId)));
        assertEquals(0, BigDecimal.valueOf(4200).compareTo(result.get(otherUserId)));
        verify(salaryHistoryRepository).findCurrentSalaries(userIds, TODAY);
    }

    @Test
    void getCurrentBaseSalaries_shouldOmitUsersWithoutStartedSalary() {
        var userIds = List.of(userId);
        when(salaryHistoryRepository.findCurrentSalaries(userIds, TODAY)).thenReturn(List.of());

        assertTrue(salaryHistoryService.getCurrentBaseSalaries(userIds).isEmpty());
    }

    @Test
    void getCurrentBaseSalaries_shouldSkipTheQuery_whenThereAreNoUsers() {
        assertTrue(salaryHistoryService.getCurrentBaseSalaries(List.of()).isEmpty());

        verify(salaryHistoryRepository, never()).findCurrentSalaries(anyCollection(), any(LocalDate.class));
    }

    @Test
    void getCurrentBaseSalary_shouldReturnTheCurrentSalary() {
        when(salaryHistoryRepository.findCurrentSalaries(List.of(userId), TODAY))
            .thenReturn(List.of(new CurrentSalary(userId, BigDecimal.valueOf(3500))));

        var result = salaryHistoryService.getCurrentBaseSalary(userId);

        assertEquals(0, BigDecimal.valueOf(3500).compareTo(result.orElseThrow()));
    }

    @Test
    void getCurrentBaseSalary_shouldBeEmpty_whenNoSalaryHasStarted() {
        when(salaryHistoryRepository.findCurrentSalaries(List.of(userId), TODAY)).thenReturn(List.of());

        assertTrue(salaryHistoryService.getCurrentBaseSalary(userId).isEmpty());
    }

    // ---- aviso ao resumo mensal (PeriodDataChangedEvent): o mês da vigência, antigo e novo ----

    @Test
    void create_shouldPublishThePeriodOfTheEffectiveDate() {
        var input = salaryOf(null, LocalDate.of(2026, 8, 1));
        when(userService.getById(userId)).thenReturn(user);
        when(salaryHistoryRepository.save(any())).thenReturn(input);

        salaryHistoryService.create(input, userId);

        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(2026, 8)));
    }

    @Test
    void create_shouldNotPublish_whenThereIsAlreadyASalaryOnThatDate() {
        var input = salaryOf(null, LocalDate.of(2026, 8, 1));
        when(userService.getById(userId)).thenReturn(user);
        when(salaryHistoryRepository.existsByUserIdAndEffectiveFrom(userId, LocalDate.of(2026, 8, 1))).thenReturn(true);

        assertThrows(BusinessException.class, () -> salaryHistoryService.create(input, userId));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void update_shouldPublishTheOlderMonth_whenTheEffectiveDateMovesToAnEarlierMonth() {
        updateMovingTheEffectiveDate(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 5, 1));

        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(2026, 5)));
    }

    @Test
    void update_shouldPublishTheOlderMonth_whenTheEffectiveDateMovesToALaterMonth() {
        updateMovingTheEffectiveDate(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 8, 1));

        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(2026, 5)));
    }

    @Test
    void update_shouldNotPublish_whenAnotherSalaryAlreadyStartsOnTheNewDate() {
        var id = UUID.randomUUID();
        var newDate = LocalDate.of(2026, 8, 1);
        when(salaryHistoryRepository.findById(id)).thenReturn(Optional.of(salaryOf(id, LocalDate.of(2026, 5, 1))));
        when(salaryHistoryRepository.findByUserIdAndEffectiveFrom(userId, newDate))
            .thenReturn(Optional.of(salaryOf(UUID.randomUUID(), newDate)));

        assertThrows(BusinessException.class, () -> salaryHistoryService.update(id, salaryOf(null, newDate)));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void delete_shouldPublishThePeriodOfTheEffectiveDate() {
        var id = UUID.randomUUID();
        when(salaryHistoryRepository.findById(id)).thenReturn(Optional.of(salaryOf(id, LocalDate.of(2026, 8, 1))));

        salaryHistoryService.delete(id);

        verify(eventPublisher).publishEvent(new PeriodDataChangedEvent(userId, YearMonth.of(2026, 8)));
    }

    @Test
    void delete_shouldNotPublish_whenTheSalaryDoesNotExist() {
        var id = UUID.randomUUID();
        when(salaryHistoryRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> salaryHistoryService.delete(id));

        verifyNoInteractions(eventPublisher);
    }

    private void updateMovingTheEffectiveDate(LocalDate previousDate, LocalDate newDate) {
        var id = UUID.randomUUID();
        when(salaryHistoryRepository.findById(id)).thenReturn(Optional.of(salaryOf(id, previousDate)));
        when(salaryHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        salaryHistoryService.update(id, salaryOf(null, newDate));
    }

    private SalaryHistory salaryOf(UUID id, LocalDate effectiveFrom) {
        var salary = SalaryHistory.builder()
            .user(user)
            .baseSalary(BigDecimal.valueOf(3000))
            .effectiveFrom(effectiveFrom)
            .build();
        salary.setId(id);
        return salary;
    }

}
