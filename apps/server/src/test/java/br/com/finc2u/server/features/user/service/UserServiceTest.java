package br.com.finc2u.server.features.user.service;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.event.UserRegisteredEvent;
import br.com.finc2u.server.features.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    @SuppressWarnings("unused") // injetado no service pelo @InjectMocks
    private Clock clock = Clock.fixed(LocalDate.of(2026, 3, 15).atStartOfDay(ZoneId.systemDefault()).toInstant(),
        ZoneId.systemDefault());

    @InjectMocks
    private UserService userService;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
            .name("Lucas Facina")
            .email("facina3@gmail.com")
            .savingsBalance(BigDecimal.valueOf(1000))
            .build();
        user.setId(userId);
    }

    @Test
    void create_shouldReturnUser_whenEmailIsUnique() {
        when(userRepository.findByEmail(anyString()))
            .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
            .thenReturn(user);

        var response = userService.create(user, BigDecimal.valueOf(5000), LocalDate.of(2026, 1, 1));

        assertNotNull(response);
        assertEquals(user.getName(), response.getName());
        assertEquals(user.getEmail(), response.getEmail());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void create_shouldPublishUserRegisteredEvent_withSalaryAndEffectiveFrom() {
        when(userRepository.findByEmail(anyString()))
            .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
            .thenReturn(user);

        userService.create(user, BigDecimal.valueOf(5000), LocalDate.of(2026, 1, 1));

        var event = captureRegisteredEvent();
        assertSame(user, event.user());
        assertEquals(0, BigDecimal.valueOf(5000).compareTo(event.baseSalary()));
        assertEquals(LocalDate.of(2026, 1, 1), event.salaryEffectiveFrom());
    }

    @Test
    void create_shouldDefaultEffectiveFromToFirstDayOfCurrentMonth_whenNotInformed() {
        when(userRepository.findByEmail(anyString()))
            .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
            .thenReturn(user);

        userService.create(user, BigDecimal.valueOf(5000), null);

        assertEquals(LocalDate.of(2026, 3, 1), captureRegisteredEvent().salaryEffectiveFrom());
    }

    @Test
    void create_shouldThrowBusinessException_whenEmailAlreadyExists() {
        when(userRepository.findByEmail(anyString()))
            .thenReturn(Optional.of(user));

        assertThrows(BusinessException.class,
            () -> userService.create(user, BigDecimal.valueOf(5000), null));
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void getById_shouldReturnUser_whenIdExists() {
        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        var response = userService.getById(userId);

        assertNotNull(response);
        assertEquals(userId, response.getId());
    }

    @Test
    void getById_shouldThrowResourceNotFoundException_whenIdDoesNotExist() {
        when(userRepository.findById(userId))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getById(userId));
    }

    @Test
    void getAll_shouldReturnListOfUser() {
        when(userRepository.findAll())
            .thenReturn(List.of(user));

        var response = userService.getAll();

        assertNotNull(response);
        assertFalse(response.isEmpty());
        assertEquals(1, response.size());
    }

    @Test
    void update_shouldReturnUpdatedUser_whenUserExists() {
        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class)))
            .thenReturn(user);

        var response = userService.update(userId, user);

        assertNotNull(response);
        assertEquals(user.getName(), response.getName());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void update_shouldCopyOnlyNameEmailAndSavingsBalance() {
        var changes = User.builder()
            .name("Lucas F.")
            .email("novo@email.com")
            .savingsBalance(BigDecimal.valueOf(2500))
            .build();
        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(userRepository.findByEmail("novo@email.com"))
            .thenReturn(Optional.empty());
        when(userRepository.save(user))
            .thenReturn(user);

        var response = userService.update(userId, changes);

        assertEquals("Lucas F.", response.getName());
        assertEquals("novo@email.com", response.getEmail());
        assertEquals(0, BigDecimal.valueOf(2500).compareTo(response.getSavingsBalance()));
    }

    @Test
    void update_shouldThrowBusinessException_whenNewEmailBelongsToAnotherUser() {
        var changes = User.builder()
            .name("Lucas Facina")
            .email("outro@email.com")
            .build();
        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(userRepository.findByEmail("outro@email.com"))
            .thenReturn(Optional.of(User.builder().build()));

        assertThrows(BusinessException.class, () -> userService.update(userId, changes));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void delete_shouldCallDelete_whenUserExists() {
        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        doNothing().when(userRepository).delete(user);

        userService.delete(userId);

        verify(userRepository, times(1)).delete(user);
    }

    private UserRegisteredEvent captureRegisteredEvent() {
        var captor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

}
