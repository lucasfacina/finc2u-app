package br.com.finc2u.server.features.card.controller;

import br.com.finc2u.server.config.GlobalExceptionHandler;
import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.features.card.dto.CardAccountRequest;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.card.service.CardAccountService;
import br.com.finc2u.server.features.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CardAccountController.class, GlobalExceptionHandler.class})
class CardAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CardAccountService cardAccountService;

    @Autowired
    private ObjectMapper objectMapper;

    User user;
    private CardAccount cardAccount;
    private CardAccountRequest request;
    private UUID userId;
    private UUID cardId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        cardId = UUID.randomUUID();

        user = User.builder()
            .name("Lucas Facina")
            .email("facina3@gmail.com")
            .build();
        user.setId(userId);

        cardAccount = CardAccount.builder()
            .cardName("Nubank")
            .closingDate(5)
            .dueDate(15)
            .user(user)
            .build();
        cardAccount.setId(cardId);

        request = new CardAccountRequest(
            "Nubank",
            5,
            15,
            null
        );
    }

    @Test
    void create_shouldReturnCreated() throws Exception {
        when(cardAccountService.create(any(CardAccount.class), eq(userId)))
            .thenReturn(cardAccount);

        mockMvc.perform(post("/card-accounts")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(cardId.toString()))
            .andExpect(jsonPath("$.cardName").value("Nubank"))
            .andExpect(jsonPath("$.userName").value("Lucas Facina"));
    }

    @Test
    void create_shouldReturnBadRequest_whenInvalidInput() throws Exception {
        var invalidRequest = new CardAccountRequest(
            "",
            null,
            null,
            null
        );

        mockMvc.perform(post("/card-accounts")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void create_shouldAcceptAndReturnTheExpirationAsYearMonth() throws Exception {
        var expiration = YearMonth.now().plusYears(4);
        cardAccount.setExpirationDate(expiration.atEndOfMonth());
        when(cardAccountService.create(any(CardAccount.class), eq(userId)))
            .thenReturn(cardAccount);

        mockMvc.perform(post("/card-accounts")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardName\":\"Nubank\",\"closingDate\":5,\"dueDate\":15,\"expiration\":\"" + expiration + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.expiration").value(expiration.toString()));

        var captor = ArgumentCaptor.forClass(CardAccount.class);
        verify(cardAccountService).create(captor.capture(), eq(userId));
        assertEquals(expiration.atEndOfMonth(), captor.getValue().getExpirationDate());
    }

    @Test
    void create_shouldReturnBadRequest_whenExpirationIsInThePast() throws Exception {
        var pastExpiration = YearMonth.now().minusMonths(1);

        mockMvc.perform(post("/card-accounts")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardName\":\"Nubank\",\"closingDate\":5,\"dueDate\":15,\"expiration\":\"" + pastExpiration + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(cardAccountService);
    }

    @Test
    void getByUser_shouldReturnOk() throws Exception {
        when(cardAccountService.getByUser(userId, null))
            .thenReturn(List.of(cardAccount));

        mockMvc.perform(get("/card-accounts")
                .param("userId", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].cardName").value("Nubank"));
    }

    @Test
    void getByUser_shouldPassTheNameToTheSearch_whenInformed() throws Exception {
        when(cardAccountService.getByUser(userId, "nu"))
            .thenReturn(List.of(cardAccount));

        mockMvc.perform(get("/card-accounts")
                .param("userId", userId.toString())
                .param("name", "nu"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].cardName").value("Nubank"));
    }

    @Test
    void create_shouldReturnUnprocessableEntity_whenNameAlreadyExists() throws Exception {
        when(cardAccountService.create(any(CardAccount.class), eq(userId)))
            .thenThrow(new BusinessException("Cartão já cadastrado!"));

        mockMvc.perform(post("/card-accounts")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void getById_shouldReturnOk() throws Exception {
        when(cardAccountService.getById(cardId))
            .thenReturn(cardAccount);

        mockMvc.perform(get("/card-accounts/{id}", cardId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(cardId.toString()));
    }

    @Test
    void update_shouldReturnOk() throws Exception {
        when(cardAccountService.update(eq(cardId), any(CardAccount.class)))
            .thenReturn(cardAccount);

        mockMvc.perform(put("/card-accounts/{id}", cardId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cardName").value("Nubank"));
    }

    @Test
    void delete_shouldReturnNoContent() throws Exception {
        doNothing().when(cardAccountService).delete(cardId);

        mockMvc.perform(delete("/card-accounts/{id}", cardId))
            .andExpect(status().isNoContent());
    }

}
