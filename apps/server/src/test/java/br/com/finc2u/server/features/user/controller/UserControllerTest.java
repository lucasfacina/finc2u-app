package br.com.finc2u.server.features.user.controller;

import br.com.finc2u.server.config.GlobalExceptionHandler;
import br.com.finc2u.server.features.user.dto.UserRequest;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.entity.UserConfiguration;
import br.com.finc2u.server.features.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {UserController.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private User user;
    private UserRequest request;
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

        request = new UserRequest(
                "Lucas Facina",
                "facina3@gmail.com",
                BigDecimal.valueOf(5000),
                BigDecimal.valueOf(1000));
    }

    @Test
    void create_shouldReturnCreated() throws Exception {
        when(userService.create(any(User.class)))
                .thenReturn(user);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.name").value("Lucas Facina"));
    }

    @Test
    void create_shouldReturnBadRequest_whenInvalidInput() throws Exception {
        UserRequest invalidRequest = new UserRequest(
                "",
                "invalid-email",
                BigDecimal.valueOf(-1),
                BigDecimal.valueOf(-1));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void getAll_shouldReturnOk() throws Exception {
        when(userService.getAll())
                .thenReturn(List.of(user));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Lucas Facina"));
    }

    @Test
    void getById_shouldReturnOk() throws Exception {
        when(userService.getById(userId))
                .thenReturn(user);

        mockMvc.perform(get("/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()));
    }

    @Test
    void update_shouldReturnOk() throws Exception {
        when(userService.update(eq(userId), any(User.class)))
                .thenReturn(user);

        mockMvc.perform(put("/users/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lucas Facina"));
    }

    @Test
    void delete_shouldReturnNoContent() throws Exception {
        doNothing().when(userService).delete(userId);

        mockMvc.perform(delete("/users/{id}", userId))
                .andExpect(status().isNoContent());
    }

}
