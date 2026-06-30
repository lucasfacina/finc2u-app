package br.com.finc2u.server.features.expense.controller;

import br.com.finc2u.server.config.GlobalExceptionHandler;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.expense.dto.ExpenseRequest;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.filter.ExpenseFilter;
import br.com.finc2u.server.features.expense.projection.FutureInstallmentProjection;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ExpenseController.class, GlobalExceptionHandler.class})
class ExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExpenseService expenseService;

    @MockBean
    private FutureInstallmentProjection futureInstallmentProjection;

    @Autowired
    private ObjectMapper objectMapper;

    private Expense expense;
    private ExpenseRequest request;
    private UUID expenseId;
    private UUID cardAccountId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        expenseId = UUID.randomUUID();
        cardAccountId = UUID.randomUUID();
        userId = UUID.randomUUID();

        CardAccount cardAccount = CardAccount.builder().cardName("Cartão Nu").build();
        cardAccount.setId(cardAccountId);

        expense = Expense.builder()
                .description("Mercado")
                .value(BigDecimal.valueOf(150.0))
                .dueDate(LocalDate.now())
                .expenseType(ExpenseType.VARIABLE)
                .paymentStatus(PaymentStatus.PENDING)
                .cardAccount(cardAccount)
                .build();
        expense.setId(expenseId);

        request = new ExpenseRequest(
                "Mercado",
                BigDecimal.valueOf(150.0),
                ExpenseType.VARIABLE,
                LocalDate.now(),
                null,
                PaymentStatus.PENDING,
                null,
                null,
                cardAccountId,
                null
        );
    }

    @Test
    void create_shouldReturnCreated() throws Exception {
        when(expenseService.create(any(Expense.class), eq(userId), eq(cardAccountId), any(), any()))
                .thenReturn(expense);

        mockMvc.perform(post("/expenses")
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(expenseId.toString()))
                .andExpect(jsonPath("$.description").value("Mercado"));
    }

    @Test
    void getAllByUserId_shouldReturnOk() throws Exception {
        when(expenseService.getByUser(userId))
                .thenReturn(List.of(expense));

        mockMvc.perform(get("/expenses")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Mercado"));
    }

    @Test
    void getWithFilters_shouldBindQueryParamsIntoFilter() throws Exception {
        when(expenseService.getWithFilters(eq(userId), any()))
                .thenReturn(List.of(expense));

        mockMvc.perform(get("/expenses/filters")
                        .param("userId", userId.toString())
                        .param("month", "5")
                        .param("year", "2026")
                        .param("status", "PAID")
                        .param("type", "PARCELED")
                        .param("cardAccountId", cardAccountId.toString())
                        .param("minPrice", "100")
                        .param("maxPrice", "500"))
                .andExpect(status().isOk());

        ArgumentCaptor<ExpenseFilter> captor = ArgumentCaptor.forClass(ExpenseFilter.class);
        verify(expenseService).getWithFilters(eq(userId), captor.capture());

        ExpenseFilter filter = captor.getValue();
        assertEquals(5, filter.month());
        assertEquals(2026, filter.year());
        assertEquals(PaymentStatus.PAID, filter.status());
        assertEquals(ExpenseType.PARCELED, filter.type());
        assertEquals(cardAccountId, filter.cardAccountId());
        assertEquals(0, new BigDecimal("100").compareTo(filter.minPrice()));
        assertEquals(0, new BigDecimal("500").compareTo(filter.maxPrice()));
    }

    @Test
    void getById_shouldReturnOk() throws Exception {
        when(expenseService.getById(expenseId))
                .thenReturn(expense);

        mockMvc.perform(get("/expenses/{id}", expenseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(expenseId.toString()));
    }

    @Test
    void update_shouldReturnOk() throws Exception {
        when(expenseService.update(eq(expenseId), any(Expense.class), eq(cardAccountId), any(), any()))
                .thenReturn(expense);

        mockMvc.perform(put("/expenses/{id}", expenseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Mercado"));
    }

    @Test
    void updateStatus_shouldReturnOk() throws Exception {
        when(expenseService.updateStatus(expenseId, PaymentStatus.PAID))
                .thenReturn(expense);

        mockMvc.perform(patch("/expenses/{id}/status", expenseId)
                        .param("status", "PAID"))
                .andExpect(status().isOk());
    }

    @Test
    void delete_shouldReturnNoContent() throws Exception {
        doNothing().when(expenseService).delete(expenseId);

        mockMvc.perform(delete("/expenses/{id}", expenseId))
                .andExpect(status().isNoContent());
    }

}
