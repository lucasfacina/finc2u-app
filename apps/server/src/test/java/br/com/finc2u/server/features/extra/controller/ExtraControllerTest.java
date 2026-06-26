package br.com.finc2u.server.features.extra.controller;


import br.com.finc2u.server.config.GlobalExceptionHandler;
import br.com.finc2u.server.features.extra.dto.ExtraRequest;
import br.com.finc2u.server.features.extra.entity.Extra;
import br.com.finc2u.server.features.extra.service.ExtraService;
import br.com.finc2u.server.features.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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

@WebMvcTest(controllers = {ExtraController.class, GlobalExceptionHandler.class})
class ExtraControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExtraService extraService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID extraId;
    private UUID userId;
    private Extra extra;
    private ExtraRequest request;

    @BeforeEach
    void setUp() {
        extraId = UUID.randomUUID();
        userId = UUID.randomUUID();

        User user = User.builder()
                .build();
        user.setId(userId);

        extra = Extra.builder()
                .description("Freela site")
                .value(BigDecimal.valueOf(800))
                .date(LocalDate.of(2026, 5, 10))
                .user(user)
                .build();
        extra.setId(extraId);

        request = new ExtraRequest(
                userId,
                "Freela site",
                BigDecimal.valueOf(800),
                LocalDate.of(2026, 5, 10)
        );
    }

    @Test
    void create_shouldReturnCreated() throws Exception {
        when(extraService.create(any(Extra.class), eq(userId))).thenReturn(extra);

        mockMvc.perform(post("/extras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(extraId.toString()))
                .andExpect(jsonPath("$.description").value("Freela site"));
    }

    @Test
    void getByUserId_shouldReturnOk() throws Exception {
        when(extraService.getByUser(userId)).thenReturn(List.of(extra));

        mockMvc.perform(get("/extras")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Freela site"));
    }

    @Test
    void getById_shouldReturnOk() throws Exception {
        when(extraService.getById(extraId)).thenReturn(extra);

        mockMvc.perform(get("/extras/{id}", extraId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(extraId.toString()));
    }

    @Test
    void update_shouldReturnOk() throws Exception {
        when(extraService.update(eq(extraId), any(Extra.class))).thenReturn(extra);

        mockMvc.perform(put("/extras/{id}", extraId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Freela site"));
    }

    @Test
    void delete_shouldReturnNoContent() throws Exception {
        doNothing().when(extraService).delete(extraId);

        mockMvc.perform(delete("/extras/{id}", extraId))
                .andExpect(status().isNoContent());
    }

}
