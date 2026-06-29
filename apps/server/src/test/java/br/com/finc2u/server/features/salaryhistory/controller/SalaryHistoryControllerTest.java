package br.com.finc2u.server.features.salaryhistory.controller;

import br.com.finc2u.server.config.GlobalExceptionHandler;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.salaryhistory.dto.SalaryHistoryRequest;
import br.com.finc2u.server.features.salaryhistory.entity.SalaryHistory;
import br.com.finc2u.server.features.salaryhistory.service.SalaryHistoryService;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {SalaryHistoryController.class, GlobalExceptionHandler.class})
class SalaryHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SalaryHistoryService salaryHistoryService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID historyId;
    private UUID userId;
    private SalaryHistory history;
    private SalaryHistoryRequest request;

    @BeforeEach
    void setUp() {
        historyId = UUID.randomUUID();
        userId = UUID.randomUUID();

        User user = User.builder().build();
        user.setId(userId);

        history = SalaryHistory.builder()
                .user(user)
                .baseSalary(BigDecimal.valueOf(3500))
                .effectiveFrom(LocalDate.of(2026, 8, 1))
                .build();
        history.setId(historyId);

        request = new SalaryHistoryRequest(
                userId,
                BigDecimal.valueOf(3500),
                LocalDate.of(2026, 8, 1)
        );
    }

    @Test
    void create_shouldReturnCreated() throws Exception {
        when(salaryHistoryService.create(any(SalaryHistory.class), eq(userId)))
                .thenReturn(history);

        mockMvc.perform(post("/salary-history")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(historyId.toString()))
                .andExpect(jsonPath("$.baseSalary").value(3500));
    }

    @Test
    void create_shouldReturnBadRequest_whenUserIdIsNull() throws Exception {
        SalaryHistoryRequest invalid = new SalaryHistoryRequest(
                null,
                BigDecimal.valueOf(3500),
                LocalDate.of(2026, 8, 1)
        );

        mockMvc.perform(post("/salary-history")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_shouldReturnBadRequest_whenBaseSalaryIsNull() throws Exception {
        SalaryHistoryRequest invalid = new SalaryHistoryRequest(
                userId,
                null,
                LocalDate.of(2026, 8, 1)
        );

        mockMvc.perform(post("/salary-history")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByUserId_shouldReturnOk() throws Exception {
        when(salaryHistoryService.getByUser(userId)).thenReturn(List.of(history));

        mockMvc.perform(get("/salary-history")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].baseSalary").value(3500));
    }

    @Test
    void getById_shouldReturnOk() throws Exception {
        when(salaryHistoryService.getById(historyId)).thenReturn(history);

        mockMvc.perform(get("/salary-history/{id}", historyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(historyId.toString()))
                .andExpect(jsonPath("$.baseSalary").value(3500));
    }

    @Test
    void getById_shouldReturnNotFound_whenNotExists() throws Exception {
        when(salaryHistoryService.getById(historyId))
                .thenThrow(new ResourceNotFoundException("Histórico de salário não encontrado com o ID: " + historyId));

        mockMvc.perform(get("/salary-history/{id}", historyId))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_shouldReturnOk() throws Exception {
        when(salaryHistoryService.update(eq(historyId), any(SalaryHistory.class)))
                .thenReturn(history);

        mockMvc.perform(put("/salary-history/{id}", historyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseSalary").value(3500));
    }

    @Test
    void delete_shouldReturnNoContent() throws Exception {
        doNothing().when(salaryHistoryService).delete(historyId);

        mockMvc.perform(delete("/salary-history/{id}", historyId))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_shouldReturnNotFound_whenNotExists() throws Exception {
        doThrow(new ResourceNotFoundException("Histórico de salário não encontrado: " + historyId))
                .when(salaryHistoryService).delete(historyId);

        mockMvc.perform(delete("/salary-history/{id}", historyId))
                .andExpect(status().isNotFound());
    }

}
