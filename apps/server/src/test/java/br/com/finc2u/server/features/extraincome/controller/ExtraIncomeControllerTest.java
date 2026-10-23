package br.com.finc2u.server.features.extraincome.controller;


import br.com.finc2u.server.config.GlobalExceptionHandler;
import br.com.finc2u.server.exception.InvalidRequestException;
import br.com.finc2u.server.features.extraincome.dto.ExtraIncomeRequest;
import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import br.com.finc2u.server.features.extraincome.service.ExtraIncomeService;
import br.com.finc2u.server.features.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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

@WebMvcTest(controllers = {ExtraIncomeController.class, GlobalExceptionHandler.class})
class ExtraIncomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExtraIncomeService extraIncomeService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID extraIncomeId;
    private UUID userId;
    private ExtraIncome extraIncome;
    private ExtraIncomeRequest request;

    @BeforeEach
    void setUp() {
        extraIncomeId = UUID.randomUUID();
        userId = UUID.randomUUID();

        var user = User.builder()
            .build();
        user.setId(userId);

        extraIncome = ExtraIncome.builder()
            .description("Freela site")
            .value(BigDecimal.valueOf(800))
            .date(LocalDate.of(2026, 5, 10))
            .user(user)
            .build();
        extraIncome.setId(extraIncomeId);

        request = new ExtraIncomeRequest(
            "Freela site",
            BigDecimal.valueOf(800),
            LocalDate.of(2026, 5, 10)
        );
    }

    @Test
    void create_shouldReturnCreated() throws Exception {
        when(extraIncomeService.create(any(ExtraIncome.class), eq(userId)))
            .thenReturn(extraIncome);

        mockMvc.perform(post("/extra-incomes")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(extraIncomeId.toString()))
            .andExpect(jsonPath("$.description").value("Freela site"));
    }

    @Test
    void getAllByUserId_shouldReturnOk() throws Exception {
        when(extraIncomeService.getByUser(userId))
            .thenReturn(List.of(extraIncome));

        mockMvc.perform(get("/extra-incomes")
                .param("userId", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].description").value("Freela site"));
    }

    @Test
    void getById_shouldReturnOk() throws Exception {
        when(extraIncomeService.getById(extraIncomeId))
            .thenReturn(extraIncome);

        mockMvc.perform(get("/extra-incomes/{id}", extraIncomeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(extraIncomeId.toString()));
    }

    @Test
    void update_shouldReturnOk() throws Exception {
        when(extraIncomeService.update(eq(extraIncomeId), any(ExtraIncome.class)))
            .thenReturn(extraIncome);

        mockMvc.perform(put("/extra-incomes/{id}", extraIncomeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Freela site"));
    }

    @Test
    void getByUserIdAndPeriod_shouldReturnOk() throws Exception {
        var start = LocalDate.of(2026, 5, 1);
        var end = LocalDate.of(2026, 5, 31);

        when(extraIncomeService.getByUserAndPeriod(userId, start, end))
            .thenReturn(List.of(extraIncome));

        mockMvc.perform(get("/extra-incomes/period")
                .param("userId", userId.toString())
                .param("startDate", start.toString())
                .param("endDate", end.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].description").value("Freela site"));
    }

    @Test
    void getByUserIdAndPeriod_shouldReturnBadRequest_whenStartIsAfterEnd() throws Exception {
        when(extraIncomeService.getByUserAndPeriod(eq(userId), any(), any()))
            .thenThrow(new InvalidRequestException("A data inicial não pode ser posterior à data final."));

        mockMvc.perform(get("/extra-incomes/period")
                .param("userId", userId.toString())
                .param("startDate", "2026-05-31")
                .param("endDate", "2026-05-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("A data inicial não pode ser posterior à data final."));
    }

    @Test
    void delete_shouldReturnNoContent() throws Exception {
        doNothing().when(extraIncomeService).delete(extraIncomeId);

        mockMvc.perform(delete("/extra-incomes/{id}", extraIncomeId))
            .andExpect(status().isNoContent());
    }

}
