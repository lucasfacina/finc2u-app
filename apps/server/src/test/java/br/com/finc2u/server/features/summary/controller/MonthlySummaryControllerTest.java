package br.com.finc2u.server.features.summary.controller;

import br.com.finc2u.server.config.GlobalExceptionHandler;
import br.com.finc2u.server.features.summary.dto.MonthlySummaryRequest;
import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.entity.MonthlySummaryId;
import br.com.finc2u.server.features.summary.service.MonthlySummaryService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {MonthlySummaryController.class, GlobalExceptionHandler.class})
class MonthlySummaryControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean  private MonthlySummaryService monthlySummaryService;
    @Autowired private ObjectMapper objectMapper;

    private UUID userId;
    private MonthlySummary summary;
    private final int MONTH = 6, YEAR = 2026;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        User user = User.builder()
                .build();
        user.setId(userId);

        summary = MonthlySummary.builder()
                .id(MonthlySummaryId.builder()
                        .userId(userId)
                        .month(MONTH)
                        .year(YEAR)
                        .build())
                .user(user)
                .totalExpenses(BigDecimal.valueOf(800))
                .totalPaid(BigDecimal.valueOf(500))
                .totalPending(BigDecimal.valueOf(300))
                .totalExtras(BigDecimal.valueOf(200))
                .cashBalance(BigDecimal.valueOf(100))
                .savingsBalance(BigDecimal.valueOf(1000))
                .remainingAmount(BigDecimal.valueOf(2500))
                .flexibleBudget(BigDecimal.valueOf(2900))
                .build();
    }

    @Test
    void createOrUpdate_shouldReturnCreated() throws Exception {
        MonthlySummaryRequest request = new MonthlySummaryRequest(userId, MONTH, YEAR, null);
        when(monthlySummaryService.calculateOrRecalculate(eq(userId), eq(MONTH), eq(YEAR), any())).thenReturn(summary);

        mockMvc.perform(post("/monthly-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.month").value(MONTH))
                .andExpect(jsonPath("$.year").value(YEAR))
                .andExpect(jsonPath("$.totalExpenses").value(800));
    }

    @Test
    void createOrUpdate_shouldReturnBadRequest_whenUserIdIsNull() throws Exception {
        MonthlySummaryRequest request = new MonthlySummaryRequest(null, MONTH, YEAR, null);

        mockMvc.perform(post("/monthly-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void createOrUpdate_shouldReturnBadRequest_whenMonthIsInvalid() throws Exception {
        MonthlySummaryRequest request = new MonthlySummaryRequest(userId, 0, YEAR, null);

        mockMvc.perform(post("/monthly-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrUpdate_shouldReturnBadRequest_whenCashBalanceIsNegative() throws Exception {
        MonthlySummaryRequest request = new MonthlySummaryRequest(userId, MONTH, YEAR, BigDecimal.valueOf(-1));

        mockMvc.perform(post("/monthly-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getHistoryByUserId_shouldReturnOk() throws Exception {
        when(monthlySummaryService.getByUser(userId)).thenReturn(List.of(summary));

        mockMvc.perform(get("/monthly-summary/history")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].month").value(MONTH))
                .andExpect(jsonPath("$[0].totalExpenses").value(800));
    }

    @Test
    void getPeriodByUserId_shouldReturnOk() throws Exception {
        when(monthlySummaryService.getByUserAndPeriod(eq(userId), eq(MONTH), eq(YEAR))).thenReturn(summary);

        mockMvc.perform(get("/monthly-summary/{userId}/{year}/{month}", userId, YEAR, MONTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(MONTH))
                .andExpect(jsonPath("$.remainingAmount").value(2500));
    }

    @Test
    void delete_shouldReturnNoContent() throws Exception {
        doNothing().when(monthlySummaryService).delete(eq(userId), eq(MONTH), eq(YEAR));

        mockMvc.perform(delete("/monthly-summary/{userId}/{year}/{month}", userId, YEAR, MONTH))
                .andExpect(status().isNoContent());
    }

}