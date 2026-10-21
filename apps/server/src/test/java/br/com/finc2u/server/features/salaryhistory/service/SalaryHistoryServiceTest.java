package br.com.finc2u.server.features.salaryhistory.service;

import br.com.finc2u.server.features.salaryhistory.entity.SalaryHistory;
import br.com.finc2u.server.features.salaryhistory.repository.SalaryHistoryRepository;
import br.com.finc2u.server.features.salaryhistory.vo.CurrentSalary;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
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
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalaryHistoryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @Mock
    private SalaryHistoryRepository salaryHistoryRepository;
    @Mock
    private UserService userService;

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
        SalaryHistory input = SalaryHistory.builder()
            .baseSalary(BigDecimal.valueOf(3500))
            .effectiveFrom(LocalDate.of(2026, 8, 1))
            .build();

        SalaryHistory saved = SalaryHistory.builder()
            .user(user)
            .baseSalary(BigDecimal.valueOf(3500))
            .effectiveFrom(LocalDate.of(2026, 8, 1))
            .build();

        when(userService.getById(userId)).thenReturn(user);
        when(salaryHistoryRepository.save(any())).thenReturn(saved);

        SalaryHistory result = salaryHistoryService.create(input, userId);

        assertEquals(0, BigDecimal.valueOf(3500).compareTo(result.getBaseSalary()));
        assertEquals(LocalDate.of(2026, 8, 1), result.getEffectiveFrom());
        verify(salaryHistoryRepository).save(any(SalaryHistory.class));
    }

    @Test
    void getByUser_shouldReturnHistoryOrderedByDateDesc() {
        SalaryHistory history1 = SalaryHistory.builder()
            .user(user)
            .baseSalary(BigDecimal.valueOf(3500))
            .effectiveFrom(LocalDate.of(2026, 8, 1))
            .build();
        SalaryHistory history2 = SalaryHistory.builder()
            .user(user)
            .baseSalary(BigDecimal.valueOf(3000))
            .effectiveFrom(LocalDate.of(2026, 1, 1))
            .build();

        when(salaryHistoryRepository.findByUserIdOrderByEffectiveFromDesc(userId)).thenReturn(List.of(history1, history2));

        List<SalaryHistory> result = salaryHistoryService.getByUser(userId);

        assertEquals(2, result.size());
        assertEquals(0, BigDecimal.valueOf(3500).compareTo(result.getFirst().getBaseSalary()));
    }

    @Test
    void getBaseSalaryForPeriod_shouldReturnHistoricalSalary_whenExists() {
        SalaryHistory history = SalaryHistory.builder()
            .user(user)
            .baseSalary(BigDecimal.valueOf(3000))
            .effectiveFrom(LocalDate.of(2026, 1, 1))
            .build();

        when(salaryHistoryRepository.findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            userId, LocalDate.of(2026, 6, 1)))
            .thenReturn(Optional.of(history));

        BigDecimal result = salaryHistoryService.getBaseSalaryForPeriod(
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

        BigDecimal result = salaryHistoryService.getBaseSalaryForPeriod(
            userId,
            LocalDate.of(2026, 6, 1)
        );

        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }

    @Test
    void getBaseSalaryForPeriod_shouldNotReturnFutureSalary() {
        SalaryHistory januarySalary = SalaryHistory.builder()
            .user(user)
            .baseSalary(BigDecimal.valueOf(3000))
            .effectiveFrom(LocalDate.of(2026, 1, 1))
            .build();

        when(salaryHistoryRepository.findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            userId, LocalDate.of(2026, 6, 1)))
            .thenReturn(Optional.of(januarySalary));

        BigDecimal result = salaryHistoryService.getBaseSalaryForPeriod(
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

        Map<UUID, BigDecimal> result = salaryHistoryService.getCurrentBaseSalaries(userIds);

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

        verify(salaryHistoryRepository, never()).findCurrentSalaries(any(Collection.class), any(LocalDate.class));
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

}
