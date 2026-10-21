package br.com.finc2u.server.features.expense.controller;

import br.com.finc2u.server.config.BusinessConstants;
import br.com.finc2u.server.config.GlobalExceptionHandler;
import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.InvalidRequestException;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.expense.dto.ExpenseRequest;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.filter.ExpenseFilter;
import br.com.finc2u.server.features.expense.projection.FutureInstallmentProjection;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.expense.vo.ExpenseCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ExpenseController.class, GlobalExceptionHandler.class})
class ExpenseControllerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }

    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseService expenseService;

    @MockitoBean
    private FutureInstallmentProjection futureInstallmentProjection;

    @Autowired
    private ObjectMapper objectMapper;

    private Expense expense;
    private ExpenseRequest request;
    private UUID expenseId;
    private UUID cardAccountId;
    private UUID categoryId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        expenseId = UUID.randomUUID();
        cardAccountId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        userId = UUID.randomUUID();

        CardAccount cardAccount = CardAccount.builder().cardName("Cartão Nu").build();
        cardAccount.setId(cardAccountId);
        Category category = Category.of("Alimentação");
        category.setId(categoryId);

        expense = Expense.builder()
            .description("Mercado")
            .value(BigDecimal.valueOf(150.0))
            .dueDate(TODAY)
            .expenseType(ExpenseType.VARIABLE)
            .paymentStatus(PaymentStatus.PENDING)
            .cardAccount(cardAccount)
            .category(category)
            .build();
        expense.setId(expenseId);

        request = new ExpenseRequest(
            "Mercado",
            BigDecimal.valueOf(150.0),
            ExpenseType.VARIABLE,
            TODAY,
            null,
            PaymentStatus.PENDING,
            null, // paidAt
            null,
            null,
            categoryId,
            cardAccountId,
            null
        );
    }

    @Test
    void create_shouldReturnCreated() throws Exception {
        when(expenseService.create(eq(userId), any(ExpenseCommand.class)))
            .thenReturn(expense);

        mockMvc.perform(post("/expenses")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(expenseId.toString()))
            .andExpect(jsonPath("$.description").value("Mercado"))
            .andExpect(jsonPath("$.category.id").value(categoryId.toString()))
            .andExpect(jsonPath("$.category.name").value("Alimentação"));
    }

    @Test
    void getById_shouldFlagOverdue_whenPendingAndPastDue() throws Exception {
        expense.setDueDate(TODAY.minusDays(1));
        when(expenseService.getById(expenseId)).thenReturn(expense);

        mockMvc.perform(get("/expenses/{id}", expenseId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overdue").value(true));
    }

    @Test
    void getById_shouldNotFlagOverdue_whenDueTodayOrPaid() throws Exception {
        when(expenseService.getById(expenseId)).thenReturn(expense);

        mockMvc.perform(get("/expenses/{id}", expenseId))
            .andExpect(jsonPath("$.overdue").value(false));

        expense.setDueDate(TODAY.minusMonths(1));
        expense.setPaymentStatus(PaymentStatus.PAID);

        mockMvc.perform(get("/expenses/{id}", expenseId))
            .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    void getById_shouldExposeTheInstallmentGroupId() throws Exception {
        var groupId = UUID.randomUUID();
        expense.setExpenseType(ExpenseType.PARCELED);
        expense.setInstallmentGroupId(groupId);
        when(expenseService.getById(expenseId)).thenReturn(expense);

        mockMvc.perform(get("/expenses/{id}", expenseId))
            .andExpect(jsonPath("$.installmentGroupId").value(groupId.toString()));
    }

    @Test
    void getAllByUserId_shouldFlagOverdueOfEachExpense() throws Exception {
        var paid = Expense.builder()
            .description("Luz")
            .value(BigDecimal.valueOf(90))
            .dueDate(TODAY.minusDays(10))
            .expenseType(ExpenseType.FIXED)
            .paymentStatus(PaymentStatus.PAID)
            .category(expense.getCategory())
            .build();
        expense.setDueDate(TODAY.minusDays(1));
        when(expenseService.getByUser(userId)).thenReturn(List.of(expense, paid));

        mockMvc.perform(get("/expenses").param("userId", userId.toString()))
            .andExpect(jsonPath("$[0].overdue").value(true))
            .andExpect(jsonPath("$[1].overdue").value(false));
    }

    @Test
    void getProjections_shouldFlagOverdueRelativeToTheClock() throws Exception {
        expense.setDueDate(TODAY.minusDays(1));
        when(futureInstallmentProjection.getProjectionsByUser(userId, TODAY))
            .thenReturn(Map.of(YearMonth.of(2026, 6), List.of(expense)));

        mockMvc.perform(get("/expenses/projections").param("userId", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$['2026-06'][0].overdue").value(true));
    }

    @Test
    void create_shouldReturnBadRequest_whenStatusIsMissingAndDueDateIsInThePast() throws Exception {
        when(expenseService.create(eq(userId), any(ExpenseCommand.class)))
            .thenThrow(new InvalidRequestException("Vencimento anterior a hoje: informe se a despesa já foi paga."));

        mockMvc.perform(post("/expenses")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Vencimento anterior a hoje: informe se a despesa já foi paga."));
    }

    @Test
    void create_shouldReturnBadRequest_whenCategoryIsMissing() throws Exception {
        var requestWithoutCategory = new ExpenseRequest(
            "Mercado",
            BigDecimal.valueOf(150.0),
            ExpenseType.VARIABLE,
            TODAY,
            null,
            PaymentStatus.PENDING,
            null, // paidAt
            null,
            null,
            null,
            cardAccountId,
            null
        );

        mockMvc.perform(post("/expenses")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestWithoutCategory)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(expenseService);
    }

    @Test
    void create_shouldReturnBadRequest_whenBothCardIdAndCardNameAreInformed() throws Exception {
        var requestWithBoth = new ExpenseRequest(
            "Mercado",
            BigDecimal.valueOf(150.0),
            ExpenseType.VARIABLE,
            TODAY,
            null,
            PaymentStatus.PENDING,
            null, // paidAt
            null,
            null,
            categoryId,
            cardAccountId,
            "Nubank"
        );

        mockMvc.perform(post("/expenses")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestWithBoth)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(expenseService);
    }

    @Test
    void create_shouldPassTheCardNameToServiceAsCommand_whenOnlyTheNameIsInformed() throws Exception {
        var requestWithCardName = new ExpenseRequest(
            "Mercado",
            BigDecimal.valueOf(150.0),
            ExpenseType.VARIABLE,
            TODAY,
            null,
            PaymentStatus.PENDING,
            null, // paidAt
            null,
            null,
            categoryId,
            null,
            "Nubank"
        );
        when(expenseService.create(eq(userId), any(ExpenseCommand.class)))
            .thenReturn(expense);

        mockMvc.perform(post("/expenses")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestWithCardName)))
            .andExpect(status().isCreated());

        var captor = ArgumentCaptor.forClass(ExpenseCommand.class);
        verify(expenseService).create(eq(userId), captor.capture());
        assertEquals("Nubank", captor.getValue().cardName());
        assertNull(captor.getValue().cardAccountId());
    }

    @Test
    void update_shouldReturnBadRequest_whenCategoryIsMissing() throws Exception {
        var requestWithoutCategory = new ExpenseRequest(
            "Mercado",
            BigDecimal.valueOf(150.0),
            ExpenseType.VARIABLE,
            TODAY,
            null,
            PaymentStatus.PENDING,
            null, // paidAt
            null,
            null,
            null,
            cardAccountId,
            null
        );

        mockMvc.perform(put("/expenses/{id}", expenseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestWithoutCategory)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(expenseService);
    }

    @Test
    void create_shouldReturnBadRequest_whenCurrentInstallmentIsZero() throws Exception {
        performCreateWithInstallments(0, 3)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Erro de validação."));

        verifyNoInteractions(expenseService);
    }

    @Test
    void create_shouldReturnBadRequest_whenCurrentInstallmentIsNegative() throws Exception {
        performCreateWithInstallments(-100000, 3)
            .andExpect(status().isBadRequest());

        verifyNoInteractions(expenseService);
    }

    @Test
    void create_shouldReturnBadRequest_whenTotalInstallmentIsZero() throws Exception {
        performCreateWithInstallments(1, 0)
            .andExpect(status().isBadRequest());

        verifyNoInteractions(expenseService);
    }

    @Test
    void create_shouldReturnBadRequest_whenTotalInstallmentExceedsMaximum() throws Exception {
        performCreateWithInstallments(1, BusinessConstants.MAX_INSTALLMENTS + 1)
            .andExpect(status().isBadRequest());

        verifyNoInteractions(expenseService);
    }

    @Test
    void create_shouldReturnBadRequest_whenCurrentInstallmentIsGreaterThanTotal() throws Exception {
        performCreateWithInstallments(5, 3)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(containsString("A parcela atual não pode ser maior que o total de parcelas.")));

        verifyNoInteractions(expenseService);
    }

    @Test
    void create_shouldReturnBadRequest_whenInstallmentsAreInformedForNonParceledType() throws Exception {
        var variableWithInstallments = new ExpenseRequest(
            "Mercado", BigDecimal.valueOf(150.0), ExpenseType.VARIABLE, TODAY, null,
            PaymentStatus.PENDING, null, 1, 3, categoryId, cardAccountId, null
        );

        mockMvc.perform(post("/expenses")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(variableWithInstallments)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(containsString("Parcelas só podem ser informadas em despesas do tipo PARCELED.")));

        verifyNoInteractions(expenseService);
    }

    @Test
    void update_shouldReturnBadRequest_whenCurrentInstallmentIsGreaterThanTotal() throws Exception {
        mockMvc.perform(put("/expenses/{id}", expenseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(parceledRequest(5, 3))))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(expenseService);
    }

    @Test
    void create_shouldReturnCreated_whenInstallmentsAreWithinLimits() throws Exception {
        when(expenseService.create(eq(userId), any(ExpenseCommand.class)))
            .thenReturn(expense);

        performCreateWithInstallments(1, BusinessConstants.MAX_INSTALLMENTS)
            .andExpect(status().isCreated());
    }

    @Test
    void update_shouldReturnBadRequest_whenCurrentInstallmentIsNegative() throws Exception {
        mockMvc.perform(put("/expenses/{id}", expenseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(parceledRequest(-1, 3))))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(expenseService);
    }

    @Test
    void create_shouldPassRequestDataToServiceAsCommand() throws Exception {
        var purchaseDate = LocalDate.of(2026, 5, 20);
        var requestWithReferences = new ExpenseRequest(
            "Mercado",
            BigDecimal.valueOf(150.0),
            ExpenseType.VARIABLE,
            null,
            purchaseDate,
            PaymentStatus.PENDING,
            null, // paidAt
            null,
            null,
            categoryId,
            cardAccountId,
            null
        );
        when(expenseService.create(eq(userId), any(ExpenseCommand.class)))
            .thenReturn(expense);

        mockMvc.perform(post("/expenses")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestWithReferences)))
            .andExpect(status().isCreated());

        var captor = ArgumentCaptor.forClass(ExpenseCommand.class);
        verify(expenseService).create(eq(userId), captor.capture());

        var command = captor.getValue();
        assertEquals("Mercado", command.expense().getDescription());
        assertEquals(cardAccountId, command.cardAccountId());
        assertEquals(categoryId, command.categoryId());
        assertEquals(purchaseDate, command.purchaseDate());
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
                .param("categoryId", categoryId.toString())
                .param("minPrice", "100")
                .param("maxPrice", "500")
                .param("overdue", "true"))
            .andExpect(status().isOk());

        ArgumentCaptor<ExpenseFilter> captor = ArgumentCaptor.forClass(ExpenseFilter.class);
        verify(expenseService).getWithFilters(eq(userId), captor.capture());

        ExpenseFilter filter = captor.getValue();
        assertEquals(5, filter.month());
        assertEquals(2026, filter.year());
        assertEquals(PaymentStatus.PAID, filter.status());
        assertEquals(ExpenseType.PARCELED, filter.type());
        assertEquals(cardAccountId, filter.cardAccountId());
        assertEquals(categoryId, filter.categoryId());
        assertEquals(0, new BigDecimal("100").compareTo(filter.minPrice()));
        assertEquals(0, new BigDecimal("500").compareTo(filter.maxPrice()));
        assertEquals(Boolean.TRUE, filter.overdue());
    }

    @Test
    void getWithFilters_shouldReturnBadRequest_whenMonthIsOutOfRange() throws Exception {
        mockMvc.perform(get("/expenses/filters")
                .param("userId", userId.toString())
                .param("month", "13")
                .param("year", "2026"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Erro de validação."));

        verifyNoInteractions(expenseService);
    }

    @Test
    void getWithFilters_shouldReturnBadRequest_whenMonthIsZero() throws Exception {
        mockMvc.perform(get("/expenses/filters")
                .param("userId", userId.toString())
                .param("month", "0")
                .param("year", "2026"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(expenseService);
    }

    @Test
    void getWithFilters_shouldReturnBadRequest_whenMonthIsInformedWithoutYear() throws Exception {
        mockMvc.perform(get("/expenses/filters")
                .param("userId", userId.toString())
                .param("month", "5"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Erro de validação."))
            .andExpect(jsonPath("$.detail").value(containsString("Informe o ano junto com o mês.")));

        verifyNoInteractions(expenseService);
    }

    @Test
    void getWithFilters_shouldReturnOk_whenOnlyYearIsInformed() throws Exception {
        when(expenseService.getWithFilters(eq(userId), any()))
            .thenReturn(List.of(expense));

        mockMvc.perform(get("/expenses/filters")
                .param("userId", userId.toString())
                .param("year", "2026"))
            .andExpect(status().isOk());
    }

    @Test
    void getWithFilters_shouldReturnBadRequest_whenYearIsBelowMinimum() throws Exception {
        mockMvc.perform(get("/expenses/filters")
                .param("userId", userId.toString())
                .param("year", "1999"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(expenseService);
    }

    @Test
    void getWithFilters_shouldReturnBadRequest_whenMinPriceIsGreaterThanMaxPrice() throws Exception {
        mockMvc.perform(get("/expenses/filters")
                .param("userId", userId.toString())
                .param("minPrice", "500")
                .param("maxPrice", "100"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(expenseService);
    }

    @Test
    void getWithFilters_shouldReturnOk_whenOnlyOnePriceBoundIsInformed() throws Exception {
        when(expenseService.getWithFilters(eq(userId), any()))
            .thenReturn(List.of(expense));

        mockMvc.perform(get("/expenses/filters")
                .param("userId", userId.toString())
                .param("minPrice", "100"))
            .andExpect(status().isOk());
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
        when(expenseService.update(eq(expenseId), any(ExpenseCommand.class)))
            .thenReturn(expense);

        mockMvc.perform(put("/expenses/{id}", expenseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Mercado"));
    }

    @Test
    void updateStatus_shouldReturnOk_andLetTheServiceDefaultPaidAt() throws Exception {
        expense.setPaymentStatus(PaymentStatus.PAID);
        expense.setPaidAt(TODAY);
        when(expenseService.updateStatus(expenseId, PaymentStatus.PAID, null))
            .thenReturn(expense);

        mockMvc.perform(patch("/expenses/{id}/status", expenseId)
                .param("status", "PAID"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentStatus").value("PAID"))
            .andExpect(jsonPath("$.paidAt").value("2026-06-15"));
    }

    @Test
    void updateStatus_shouldPassInformedPaidAtToTheService() throws Exception {
        expense.setPaymentStatus(PaymentStatus.PAID);
        expense.setPaidAt(LocalDate.of(2026, 6, 10));
        when(expenseService.updateStatus(expenseId, PaymentStatus.PAID, LocalDate.of(2026, 6, 10)))
            .thenReturn(expense);

        mockMvc.perform(patch("/expenses/{id}/status", expenseId)
                .param("status", "PAID")
                .param("paidAt", "2026-06-10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paidAt").value("2026-06-10"));
    }

    @Test
    void updateStatus_shouldReturnUnprocessableContent_whenPaidAtIsRejected() throws Exception {
        when(expenseService.updateStatus(eq(expenseId), eq(PaymentStatus.PENDING), any()))
            .thenThrow(new BusinessException("A data do pagamento só pode ser informada para despesa paga."));

        mockMvc.perform(patch("/expenses/{id}/status", expenseId)
                .param("status", "PENDING")
                .param("paidAt", "2026-06-10"))
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    void create_shouldPassPaidAtFromTheRequestToTheService() throws Exception {
        var paidRequest = new ExpenseRequest(
            "Mercado", BigDecimal.valueOf(150.0), ExpenseType.VARIABLE, TODAY.minusDays(2), null,
            PaymentStatus.PAID, LocalDate.of(2026, 6, 14), null, null, categoryId, cardAccountId, null
        );
        when(expenseService.create(eq(userId), any(ExpenseCommand.class)))
            .thenReturn(expense);

        mockMvc.perform(post("/expenses")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paidRequest)))
            .andExpect(status().isCreated());

        var captor = ArgumentCaptor.forClass(ExpenseCommand.class);
        verify(expenseService).create(eq(userId), captor.capture());
        assertEquals(LocalDate.of(2026, 6, 14), captor.getValue().expense().getPaidAt());
    }

    @Test
    void delete_shouldReturnNoContent() throws Exception {
        doNothing().when(expenseService).delete(expenseId);

        mockMvc.perform(delete("/expenses/{id}", expenseId))
            .andExpect(status().isNoContent());
    }

    // As parcelas entram por parâmetro: com literais no construtor o IDE avisa que a chamada "sempre falha" na validação.
    private ExpenseRequest parceledRequest(Integer currentInstallment, Integer totalInstallment) {
        return new ExpenseRequest(
            "Compra parcelada", BigDecimal.valueOf(100.0), ExpenseType.PARCELED, TODAY, null,
            PaymentStatus.PENDING, null, currentInstallment, totalInstallment, categoryId, cardAccountId, null
        );
    }

    private ResultActions performCreateWithInstallments(Integer currentInstallment, Integer totalInstallment) throws Exception {
        return mockMvc.perform(post("/expenses")
            .param("userId", userId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(parceledRequest(currentInstallment, totalInstallment))));
    }

}
