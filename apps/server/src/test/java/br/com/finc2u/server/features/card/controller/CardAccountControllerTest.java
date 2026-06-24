package br.com.finc2u.server.features.card.controller;

import br.com.finc2u.server.config.GlobalExceptionHandler;
import br.com.finc2u.server.features.card.dto.CardAccountRequest;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.card.service.CardAccountService;
import br.com.finc2u.server.features.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CardAccountController.class, GlobalExceptionHandler.class})
class CardAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
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
                15
        );
    }

    @Test
    void create_shouldReturnCreated() throws Exception {
        when(cardAccountService.create(any(CardAccount.class), eq(userId))).thenReturn(cardAccount);

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
        CardAccountRequest invalidRequest = new CardAccountRequest(
                "",
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
    void getByUser_shouldReturnOk() throws Exception {
        when(cardAccountService.getByUser(userId)).thenReturn(List.of(cardAccount));

        mockMvc.perform(get("/card-accounts")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cardName").value("Nubank"));
    }

    @Test
    void getById_shouldReturnOk() throws Exception {
        when(cardAccountService.getById(cardId)).thenReturn(cardAccount);

        mockMvc.perform(get("/card-accounts/{id}", cardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cardId.toString()));
    }

    @Test
    void update_shouldReturnOk() throws Exception {
        when(cardAccountService.update(eq(cardId), any(CardAccount.class))).thenReturn(cardAccount);

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
