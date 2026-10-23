package br.com.finc2u.server.features.user.controller;

import br.com.finc2u.server.config.FixedClockTestConfig;
import br.com.finc2u.server.config.GlobalExceptionHandler;
import br.com.finc2u.server.features.user.dto.UserRequest;
import br.com.finc2u.server.features.user.dto.UserUpdateRequest;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.form.UserResponse;
import br.com.finc2u.server.features.user.projection.UserProfileProjection;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
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
@Import(FixedClockTestConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserProfileProjection userProfileProjection;

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
            .savingsBalance(BigDecimal.valueOf(1000))
            .build();
        user.setId(userId);

        request = new UserRequest(
            "Lucas Facina",
            "facina3@gmail.com",
            BigDecimal.valueOf(5000),
            BigDecimal.valueOf(1000),
            LocalDate.of(2026, 1, 1));
    }

    @Test
    void create_shouldReturnCreated() throws Exception {
        when(userService.create(any(User.class), eq(BigDecimal.valueOf(5000)), eq(LocalDate.of(2026, 1, 1))))
            .thenReturn(user);
        when(userProfileProjection.toProfile(user)).thenReturn(profile());

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(userId.toString()))
            .andExpect(jsonPath("$.name").value("Lucas Facina"))
            .andExpect(jsonPath("$.baseSalary").value(5000));
    }

    @Test
    void create_shouldReturnBadRequest_whenInvalidInput() throws Exception {
        var invalidRequest = new UserRequest(
            "",
            "invalid-email",
            BigDecimal.valueOf(-1),
            BigDecimal.valueOf(-1),
            null);

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void getAll_shouldReturnOk() throws Exception {
        when(userProfileProjection.getProfiles())
            .thenReturn(List.of(profile()));

        mockMvc.perform(get("/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Lucas Facina"))
            .andExpect(jsonPath("$[0].baseSalary").value(5000));
    }

    @Test
    void getById_shouldReturnOk() throws Exception {
        when(userProfileProjection.getProfile(userId))
            .thenReturn(profile());

        mockMvc.perform(get("/users/{id}", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(userId.toString()))
            .andExpect(jsonPath("$.baseSalary").value(5000));
    }

    @Test
    void update_shouldReturnOk() throws Exception {
        var updateRequest = new UserUpdateRequest("Lucas Facina", "facina3@gmail.com", BigDecimal.valueOf(1000));
        when(userService.update(eq(userId), any(User.class)))
            .thenReturn(user);
        when(userProfileProjection.toProfile(user)).thenReturn(profile());

        mockMvc.perform(put("/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Lucas Facina"));
    }

    @Test
    void update_shouldReturnBadRequest_whenInvalidInput() throws Exception {
        var invalidRequest = new UserUpdateRequest("", "invalid-email", BigDecimal.valueOf(-1));

        mockMvc.perform(put("/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void delete_shouldReturnNoContent() throws Exception {
        doNothing().when(userService).delete(userId);

        mockMvc.perform(delete("/users/{id}", userId))
            .andExpect(status().isNoContent());
    }

    private UserResponse profile() {
        return UserResponse.from(user, BigDecimal.valueOf(5000));
    }

}
