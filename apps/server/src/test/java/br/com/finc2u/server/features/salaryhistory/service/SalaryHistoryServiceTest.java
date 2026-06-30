package br.com.finc2u.server.features.salaryhistory.service;

import br.com.finc2u.server.features.salaryhistory.entity.SalaryHistory;
import br.com.finc2u.server.features.salaryhistory.repository.SalaryHistoryRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.entity.UserConfiguration;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalaryHistoryServiceTest {

    @Mock
    private SalaryHistoryRepository salaryHistoryRepository;
    @Mock
    private UserService userService;

    @InjectMocks
    private SalaryHistoryService salaryHistoryService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .userConfiguration(UserConfiguration.builder()
                        .baseSalary(BigDecimal.valueOf(3000))
                        .build())
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
                LocalDate.of(2026, 6, 1),
                BigDecimal.valueOf(9999)
        );

        assertEquals(0, BigDecimal.valueOf(3000).compareTo(result));
    }

    @Test
    void getBaseSalaryForPeriod_shouldUseFallback_whenNoHistoryExists() {
        when(salaryHistoryRepository.findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                userId, LocalDate.of(2026, 6, 1)))
                .thenReturn(Optional.empty());

        BigDecimal result = salaryHistoryService.getBaseSalaryForPeriod(
                userId,
                LocalDate.of(2026, 6, 1),
                BigDecimal.valueOf(2500)
        );

        assertEquals(0, BigDecimal.valueOf(2500).compareTo(result));
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
                LocalDate.of(2026, 6, 1),
                BigDecimal.ZERO
        );

        assertEquals(0, BigDecimal.valueOf(3000).compareTo(result));
    }

}
