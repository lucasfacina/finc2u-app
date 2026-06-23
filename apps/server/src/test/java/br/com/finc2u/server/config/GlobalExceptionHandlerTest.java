package br.com.finc2u.server.config;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ExceptionTestController.class, GlobalExceptionHandlerTest.class})
public class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    // HTTP 404
    @Test
    void shouldHandleResourceNotFoundException() throws Exception {
        mockMvc.perform(get("/test/exceptions/resource-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Recurso não encontrado."))
                .andExpect(jsonPath("$.detail").value("O recurso solicitado não foi encontrado."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // HTTP 422
    @Test
    void shouldHandleBusinessException() throws Exception {
        mockMvc.perform(get("/test/exceptions/business"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("Erro de negócio."))
                .andExpect(jsonPath("$.detail").value("Regra de negócio violada."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // HTTP 409
    @Test
    void shouldHandleDataIntegrityViolationException() throws Exception {
        mockMvc.perform(get("/test/exceptions/data-integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Operação não permitida, existem registros dependentes vinculados a este recurso."))
                .andExpect(jsonPath("$.detail").value("Violação de integridade referencial."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // HTTP 500
    void shouldHandleGenericException() throws Exception {
        mockMvc.perform(get("/test/exceptions/generic"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Erro interno no servidor."))
                .andExpect(jsonPath("$.detail").value("Ocorreu um erro inesperado no servidor."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // HTTP 400
    @Test
    void shouldHandleValidationException() throws Exception {
        mockMvc.perform(post("/test/exceptions/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("valida")))
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("Nome é obrigatório.")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

}
