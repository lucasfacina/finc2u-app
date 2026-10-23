package br.com.finc2u.server.features.user.projection;

import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.port.CurrentSalaryPort;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileProjectionTest {

    @Mock
    private UserService userService;

    @Mock
    private CurrentSalaryPort currentSalaryPort;

    private UserProfileProjection projection;
    private User ana;
    private User beto;

    @BeforeEach
    void setUp() {
        projection = new UserProfileProjection(userService, currentSalaryPort);
        ana = user("Ana", "ana@email.com");
        beto = user("Beto", "beto@email.com");
    }

    @Test
    void toProfile_shouldIncludeTheCurrentSalary() {
        when(currentSalaryPort.getCurrentBaseSalary(ana.getId())).thenReturn(Optional.of(BigDecimal.valueOf(3500)));

        var result = projection.toProfile(ana);

        assertEquals(ana.getId(), result.id());
        assertEquals(0, BigDecimal.valueOf(3500).compareTo(result.baseSalary()));
        assertEquals(0, BigDecimal.valueOf(100).compareTo(result.savingsBalance()));
    }

    @Test
    void toProfile_shouldLeaveSalaryNull_whenNoSalaryHasStarted() {
        when(currentSalaryPort.getCurrentBaseSalary(ana.getId())).thenReturn(Optional.empty());

        assertNull(projection.toProfile(ana).baseSalary());
    }

    @Test
    void getProfile_shouldLoadTheUserAndItsCurrentSalary() {
        when(userService.getById(ana.getId())).thenReturn(ana);
        when(currentSalaryPort.getCurrentBaseSalary(ana.getId())).thenReturn(Optional.of(BigDecimal.valueOf(3500)));

        var result = projection.getProfile(ana.getId());

        assertEquals("Ana", result.name());
        assertEquals(0, BigDecimal.valueOf(3500).compareTo(result.baseSalary()));
    }

    @Test
    void getProfiles_shouldFetchAllSalariesInOneCall() {
        when(userService.getAll()).thenReturn(List.of(ana, beto));
        when(currentSalaryPort.getCurrentBaseSalaries(List.of(ana.getId(), beto.getId())))
            .thenReturn(Map.of(ana.getId(), BigDecimal.valueOf(3500)));

        var result = projection.getProfiles();

        assertEquals(2, result.size());
        assertEquals(0, BigDecimal.valueOf(3500).compareTo(result.get(0).baseSalary()));
        assertNull(result.get(1).baseSalary());
        verify(currentSalaryPort, never()).getCurrentBaseSalary(ana.getId());
    }

    private User user(String name, String email) {
        var user = User.builder()
            .name(name)
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build();
        user.setId(UUID.randomUUID());
        return user;
    }

}
