package br.com.finc2u.server.features.category.listener;

import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.event.UserRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CategoryDefaultsListenerTest {

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private CategoryDefaultsListener categoryDefaultsListener;

    @Test
    void onUserRegistered_shouldCreateDefaultCategoriesForTheRegisteredUser() {
        var user = User.builder()
            .name("Ana")
            .email("ana@email.com")
            .build();
        var event = new UserRegisteredEvent(user, BigDecimal.valueOf(3000), LocalDate.of(2026, 10, 1));

        categoryDefaultsListener.onUserRegistered(event);

        verify(categoryService).createDefaults(user);
    }

    @Test
    void onUserRegistered_shouldPropagateFailure_soTheRegistrationIsRolledBack() {
        var user = User.builder()
            .build();
        var event = new UserRegisteredEvent(user, BigDecimal.valueOf(3000), LocalDate.of(2026, 10, 1));
        doThrow(new IllegalStateException("falha ao gravar categorias"))
            .when(categoryService).createDefaults(user);

        assertThrows(IllegalStateException.class, () -> categoryDefaultsListener.onUserRegistered(event));
    }

}
