package br.com.finc2u.server.features.extra.service;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.extra.entity.Extra;
import br.com.finc2u.server.features.extra.repository.ExtraRepository;
import br.com.finc2u.server.features.user.entity.User;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExtraServiceTest {

    @Mock
    private ExtraRepository extraRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ExtraService extraService;

    private UUID extraId;
    private UUID userId;
    private User user;
    private Extra extra;

    @BeforeEach
    void setUp() {
        extraId = UUID.randomUUID();
        userId = UUID.randomUUID();

        user = User.builder()
                .build();
        user.setId(userId);

        extra = Extra.builder()
                .description("Freela site")
                .value(BigDecimal.valueOf(800))
                .date(LocalDate.of(2026, 5, 10))
                .user(user)
                .build();
        extra.setId(extraId);
    }

    @Test
    void create_shouldAssociateUserAndSave() {
        when(userService.getById(userId))
                .thenReturn(user);
        when(extraRepository.save(any(Extra.class)))
                .thenReturn(extra);

        Extra result = extraService.create(extra, userId);

        assertNotNull(result);
        assertEquals(user, result.getUser());
        verify(extraRepository, times(1)).save(extra);
    }

    @Test
    void getById_shouldReturnExtra_whenExists() {
        when(extraRepository.findById(extraId))
                .thenReturn(Optional.of(extra));

        Extra result = extraService.getById(extraId);

        assertNotNull(result);
        assertEquals(extraId, result.getId());
    }

    @Test
    void getById_shouldThrow_whenNotFound() {
        when(extraRepository.findById(extraId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> extraService.getById(extraId));
    }

    @Test
    void getByUser_shouldReturnList() {
        when(userService.getById(userId))
                .thenReturn(user);
        when(extraRepository.findByUserId(userId))
                .thenReturn(List.of(extra));

        List<Extra> result = extraService.getByUser(userId);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void update_shouldUpdateFieldsAndSave() {
        Extra updates = Extra.builder()
                .description("Freela app")
                .value(BigDecimal.valueOf(1200))
                .date(LocalDate.of(2026, 5, 20))
                .build();

        when(extraRepository.findById(extraId))
                .thenReturn(Optional.of(extra));
        when(extraRepository.save(any(Extra.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Extra result = extraService.update(extraId, updates);

        assertEquals("Freela app", result.getDescription());
        assertEquals(0, BigDecimal.valueOf(1200).compareTo(result.getValue()));
        assertEquals(LocalDate.of(2026, 5, 20), result.getDate());
        verify(extraRepository, times(1)).save(extra);
    }

    @Test
    void delete_shouldCallRepositoryDelete() {
        when(extraRepository.findById(extraId))
                .thenReturn(Optional.of(extra));
        doNothing().when(extraRepository).delete(extra);

        extraService.delete(extraId);

        verify(extraRepository, times(1)).delete(extra);
    }

    @Test
    void delete_shouldThrow_whenNotFound() {
        when(extraRepository.findById(extraId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> extraService.delete(extraId));
    }

}
