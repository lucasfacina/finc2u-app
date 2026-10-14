package br.com.finc2u.server.features.user.dto;

import br.com.finc2u.server.features.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class UserRequestTest {

    @Test
    void toEntity_shouldBuildUserWithConfigurationLinkedBack() {
        UserRequest request = new UserRequest(
                "Lucas",
                "lucas@email.com",
                BigDecimal.valueOf(3000),
                BigDecimal.valueOf(500)
        );

        User user = request.toEntity();

        assertEquals("Lucas", user.getName());
        assertEquals("lucas@email.com", user.getEmail());
        assertNotNull(user.getUserConfiguration());
        assertEquals(0, BigDecimal.valueOf(3000).compareTo(user.getUserConfiguration().getBaseSalary()));
        assertEquals(0, BigDecimal.valueOf(500).compareTo(user.getUserConfiguration().getSavingsBalance()));
        assertSame(user, user.getUserConfiguration().getUser());
    }

}
