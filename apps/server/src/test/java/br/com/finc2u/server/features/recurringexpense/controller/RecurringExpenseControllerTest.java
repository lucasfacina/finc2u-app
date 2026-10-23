package br.com.finc2u.server.features.recurringexpense.controller;

import br.com.finc2u.server.config.FixedClockTestConfig;
import br.com.finc2u.server.config.GlobalExceptionHandler;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.recurringexpense.dto.RecurringExpenseRequest;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.service.RecurringExpenseService;
import br.com.finc2u.server.features.recurringexpense.vo.RecurringExpenseCommand;
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
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {RecurringExpenseController.class, GlobalExceptionHandler.class})
@Import(FixedClockTestConfig.class)
class RecurringExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecurringExpenseService recurringExpenseService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID userId;
    private UUID recurringExpenseId;
    private UUID categoryId;
    private RecurringExpense recurringExpense;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        recurringExpenseId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        var category = Category.builder()
            .name("Assinaturas")
            .build();
        category.setId(categoryId);
        var card = CardAccount.builder()
            .cardName("Nubank")
            .build();

        recurringExpense = RecurringExpense.of("Spotify", BigDecimal.valueOf(21.90), 5, YearMonth.of(2026, 10), YearMonth.of(2027, 3));
        recurringExpense.setId(recurringExpenseId);
        recurringExpense.setCategory(category);
        recurringExpense.setCardAccount(card);
    }

    @Test
    void create_shouldReturnCreated() throws Exception {
        when(recurringExpenseService.create(any(RecurringExpenseCommand.class), eq(userId)))
            .thenReturn(recurringExpense);

        mockMvc.perform(post("/recurring-expenses")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(recurringExpenseId.toString()))
            .andExpect(jsonPath("$.description").value("Spotify"))
            .andExpect(jsonPath("$.dayOfMonth").value(5))
            .andExpect(jsonPath("$.startMonth").value("2026-10"))
            .andExpect(jsonPath("$.endMonth").value("2027-03"))
            .andExpect(jsonPath("$.cardName").value("Nubank"))
            .andExpect(jsonPath("$.category.name").value("Assinaturas"));
    }

    @Test
    void create_shouldPassTheReferencesToTheServiceAsACommand() throws Exception {
        var cardId = UUID.randomUUID();
        when(recurringExpenseService.create(any(RecurringExpenseCommand.class), eq(userId)))
            .thenReturn(recurringExpense);

        mockMvc.perform(post("/recurring-expenses")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request("Spotify", BigDecimal.TEN, 5, YearMonth.of(2026, 10), null, categoryId, cardId))))
            .andExpect(status().isCreated());

        verify(recurringExpenseService).create(argThat(command ->
            command.categoryId().equals(categoryId)
                && cardId.equals(command.cardAccountId())
                && command.recurringExpense().getEndMonth() == null), eq(userId));
    }

    @Test
    void create_shouldReturnBadRequest_whenUserIdIsMissing() throws Exception {
        mockMvc.perform(post("/recurring-expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(recurringExpenseService);
    }

    @Test
    void create_shouldReturnBadRequest_whenDescriptionIsBlank() throws Exception {
        assertCreateIsRejected(request(" ", BigDecimal.TEN, 5, YearMonth.of(2026, 10), null, categoryId, null));
    }

    @Test
    void create_shouldReturnBadRequest_whenValueIsNotPositive() throws Exception {
        assertCreateIsRejected(request("Spotify", BigDecimal.ZERO, 5, YearMonth.of(2026, 10), null, categoryId, null));
    }

    @Test
    void create_shouldReturnBadRequest_whenDayOfMonthIsBelowOne() throws Exception {
        assertCreateIsRejected(request("Spotify", BigDecimal.TEN, 0, YearMonth.of(2026, 10), null, categoryId, null));
    }

    @Test
    void create_shouldReturnBadRequest_whenDayOfMonthIsAboveThirtyOne() throws Exception {
        assertCreateIsRejected(request("Spotify", BigDecimal.TEN, 32, YearMonth.of(2026, 10), null, categoryId, null));
    }

    @Test
    void create_shouldReturnBadRequest_whenStartMonthIsMissing() throws Exception {
        assertCreateIsRejected(request("Spotify", BigDecimal.TEN, 5, null, null, categoryId, null));
    }

    @Test
    void create_shouldPassTheCategoryNameToTheServiceAsACommand_whenOnlyTheNameIsInformed() throws Exception {
        when(recurringExpenseService.create(any(RecurringExpenseCommand.class), eq(userId)))
            .thenReturn(recurringExpense);

        mockMvc.perform(post("/recurring-expenses")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestWithCategoryName(null, "Pets"))))
            .andExpect(status().isCreated());

        verify(recurringExpenseService).create(argThat(command ->
            command.categoryId() == null && "Pets".equals(command.categoryName())), eq(userId));
    }

    @Test
    void create_shouldReturnBadRequest_whenCategoryComesByIdAndByName() throws Exception {
        assertCreateIsRejected(requestWithCategoryName(categoryId, "Pets"));
    }

    @Test
    void create_shouldReturnBadRequest_whenCategoryNameIsBlankAndThereIsNoId() throws Exception {
        assertCreateIsRejected(requestWithCategoryName(null, "   "));
    }

    @Test
    void create_shouldReturnBadRequest_whenCategoryNameIsLongerThan60Characters() throws Exception {
        assertCreateIsRejected(requestWithCategoryName(null, "x".repeat(61)));
    }

    @Test
    void create_shouldReturnBadRequest_whenCategoryIsMissing() throws Exception {
        assertCreateIsRejected(request("Spotify", BigDecimal.TEN, 5, YearMonth.of(2026, 10), null, null, null));
    }

    @Test
    void create_shouldReturnBadRequest_whenEndMonthIsBeforeStartMonth() throws Exception {
        assertCreateIsRejected(request("Spotify", BigDecimal.TEN, 5, YearMonth.of(2026, 10), YearMonth.of(2026, 9), categoryId, null));
    }

    @Test
    void create_shouldAcceptEndMonthEqualToStartMonth() throws Exception {
        when(recurringExpenseService.create(any(RecurringExpenseCommand.class), eq(userId)))
            .thenReturn(recurringExpense);

        mockMvc.perform(post("/recurring-expenses")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request("Spotify", BigDecimal.TEN, 5, YearMonth.of(2026, 10), YearMonth.of(2026, 10), categoryId, null))))
            .andExpect(status().isCreated());
    }

    @Test
    void create_shouldReturnNotFound_whenTheCategoryIsNotTheOwners() throws Exception {
        when(recurringExpenseService.create(any(RecurringExpenseCommand.class), eq(userId)))
            .thenThrow(new ResourceNotFoundException("Categoria não encontrada"));

        mockMvc.perform(post("/recurring-expenses")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isNotFound());
    }

    @Test
    void getAllByUserId_shouldReturnOk() throws Exception {
        when(recurringExpenseService.getByUser(userId))
            .thenReturn(List.of(recurringExpense));

        mockMvc.perform(get("/recurring-expenses")
                .param("userId", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].description").value("Spotify"));
    }

    @Test
    void getAllByUserId_shouldReturnBadRequest_whenUserIdIsMissing() throws Exception {
        mockMvc.perform(get("/recurring-expenses"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getById_shouldReturnOk() throws Exception {
        when(recurringExpenseService.getById(recurringExpenseId))
            .thenReturn(recurringExpense);

        mockMvc.perform(get("/recurring-expenses/{id}", recurringExpenseId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(recurringExpenseId.toString()));
    }

    @Test
    void getById_shouldReturnNotFound_whenMissing() throws Exception {
        when(recurringExpenseService.getById(recurringExpenseId))
            .thenThrow(new ResourceNotFoundException("Despesa recorrente não encontrada"));

        mockMvc.perform(get("/recurring-expenses/{id}", recurringExpenseId))
            .andExpect(status().isNotFound());
    }

    @Test
    void update_shouldReturnOk() throws Exception {
        when(recurringExpenseService.update(eq(recurringExpenseId), any(RecurringExpenseCommand.class)))
            .thenReturn(recurringExpense);

        mockMvc.perform(put("/recurring-expenses/{id}", recurringExpenseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Spotify"));
    }

    @Test
    void update_shouldReturnBadRequest_whenDayOfMonthIsInvalid() throws Exception {
        mockMvc.perform(put("/recurring-expenses/{id}", recurringExpenseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request("Spotify", BigDecimal.TEN, 40, YearMonth.of(2026, 10), null, categoryId, null))))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(recurringExpenseService);
    }

    @Test
    void delete_shouldReturnNoContent() throws Exception {
        doNothing().when(recurringExpenseService).delete(recurringExpenseId);

        mockMvc.perform(delete("/recurring-expenses/{id}", recurringExpenseId))
            .andExpect(status().isNoContent());
    }

    @Test
    void delete_shouldReturnNotFound_whenMissing() throws Exception {
        doThrow(new ResourceNotFoundException("Despesa recorrente não encontrada"))
            .when(recurringExpenseService).delete(recurringExpenseId);

        mockMvc.perform(delete("/recurring-expenses/{id}", recurringExpenseId))
            .andExpect(status().isNotFound());
    }

    private void assertCreateIsRejected(RecurringExpenseRequest invalidRequest) throws Exception {
        mockMvc.perform(post("/recurring-expenses")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(recurringExpenseService);
    }

    private RecurringExpenseRequest validRequest() {
        return request("Spotify", BigDecimal.valueOf(21.90), 5, YearMonth.of(2026, 10), YearMonth.of(2027, 3), categoryId, null);
    }

    private RecurringExpenseRequest requestWithCategoryName(UUID categoryId, String categoryName) {
        return new RecurringExpenseRequest("Ração", BigDecimal.TEN, 5, YearMonth.of(2026, 10), null, categoryId, categoryName, null);
    }

    private RecurringExpenseRequest request(String description, BigDecimal value, Integer dayOfMonth, YearMonth startMonth,
                                            YearMonth endMonth, UUID categoryId, UUID cardAccountId) {
        return new RecurringExpenseRequest(description, value, dayOfMonth, startMonth, endMonth, categoryId, null, cardAccountId);
    }

}
