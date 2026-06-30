package br.com.finc2u.server.features.user.service;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.entity.UserConfiguration;
import br.com.finc2u.server.features.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
                .userConfiguration(UserConfiguration.builder()
                        .baseSalary(BigDecimal.valueOf(5000))
                        .savingsBalance(BigDecimal.valueOf(1000))
                        .build()
                )
                .build();
        user.setId(userId);
    }

    @Test
    void create_shouldReturnUser_whenEmailIsUnique() {
        when(userRepository.findByEmail(anyString()))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenReturn(user);

        User response = userService.create(user);

        assertNotNull(response);
        assertEquals(user.getName(), response.getName());
        assertEquals(user.getEmail(), response.getEmail());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void create_shouldThrowBusinessException_whenEmailAlreadyExists() {
        when(userRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(user));

        assertThrows(BusinessException.class, () -> userService.create(user));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getById_shouldReturnUser_whenIdExists() {
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        User response = userService.getById(userId);

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

        List<User> response = userService.getAll();

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

        User response = userService.update(userId, user);

        assertNotNull(response);
        assertEquals(user.getName(), response.getName());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void delete_shouldCallDelete_whenUserExists() {
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        doNothing().when(userRepository).delete(user);

        userService.delete(userId);

        verify(userRepository, times(1)).delete(user);
    }

}
