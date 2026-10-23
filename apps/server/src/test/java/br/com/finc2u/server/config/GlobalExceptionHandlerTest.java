package br.com.finc2u.server.config;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ExceptionTestController.class, GlobalExceptionHandler.class})
@Import(FixedClockTestConfig.class)
public class GlobalExceptionHandlerTest {

    // `FixedClockTestConfig.NOW` como o Jackson o serializa
    private static final String NOW = "2026-06-15T10:30:00";

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
            .andExpect(jsonPath("$.timestamp").value(NOW));
    }

    // HTTP 422
    @Test
    void shouldHandleBusinessException() throws Exception {
        mockMvc.perform(get("/test/exceptions/business"))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.message").value("Erro de negócio."))
            .andExpect(jsonPath("$.detail").value("Regra de negócio violada."))
            .andExpect(jsonPath("$.timestamp").value(NOW));
    }

    // HTTP 400
    @Test
    void shouldHandleInvalidRequestException() throws Exception {
        mockMvc.perform(get("/test/exceptions/invalid-request"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Requisição inválida."))
            .andExpect(jsonPath("$.detail").value("A requisição não pôde ser processada."))
            .andExpect(jsonPath("$.timestamp").value(NOW));
    }

    // HTTP 409
    @Test
    void shouldHandleDataIntegrityViolationException() throws Exception {
        mockMvc.perform(get("/test/exceptions/data-integrity"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.message").value("Operação não permitida, existem registros dependentes vinculados a este recurso."))
            .andExpect(jsonPath("$.detail").value("Violação de integridade referencial."))
            .andExpect(jsonPath("$.timestamp").value(NOW));
    }

    // HTTP 409 — unicidade: mensagem própria, sem expor o nome da constraint
    @Test
    void shouldHandleUniqueViolation_withUniquenessMessage() throws Exception {
        mockMvc.perform(get("/test/exceptions/data-integrity-unique"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.message").value("Já existe um registro com estes dados."))
            .andExpect(jsonPath("$.detail").value("Violação de unicidade."))
            .andExpect(content().string(Matchers.not(Matchers.containsString("uk_user_email"))));
    }

    // HTTP 409 — DuplicateKeyException é a mesma violação de unicidade
    @Test
    void shouldHandleDuplicateKeyException_withUniquenessMessage() throws Exception {
        mockMvc.perform(get("/test/exceptions/data-integrity-duplicate-key"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Já existe um registro com estes dados."))
            .andExpect(jsonPath("$.detail").value("Violação de unicidade."))
            .andExpect(content().string(Matchers.not(Matchers.containsString("uk_user_email"))));
    }

    // HTTP 409 — chave estrangeira mantém a mensagem de registros dependentes
    @Test
    void shouldHandleForeignKeyViolation_withDependentRecordsMessage() throws Exception {
        mockMvc.perform(get("/test/exceptions/data-integrity-foreign-key"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Operação não permitida, existem registros dependentes vinculados a este recurso."))
            .andExpect(jsonPath("$.detail").value("Violação de integridade referencial."));
    }

    // HTTP 500 — detalhe fixo; a mensagem interna da exceção não chega ao cliente
    @Test
    void shouldHandleGenericException_withoutExposingInternalMessage() throws Exception {
        mockMvc.perform(get("/test/exceptions/generic"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.message").value("Erro interno no servidor."))
            .andExpect(jsonPath("$.detail").value("Ocorreu um erro inesperado. Tente novamente mais tarde."))
            .andExpect(content().string(Matchers.not(Matchers.containsString("tb_expense"))))
            .andExpect(jsonPath("$.timestamp").value(NOW));
    }

    // HTTP 400 — parâmetro obrigatório ausente
    @Test
    void shouldHandleMissingServletRequestParameterException() throws Exception {
        mockMvc.perform(get("/test/exceptions/missing-param"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Parâmetro obrigatório ausente."))
            .andExpect(jsonPath("$.detail").value("O parâmetro 'userId' é obrigatório."))
            .andExpect(jsonPath("$.timestamp").value(NOW));
    }

    // HTTP 404 — rota inexistente
    @Test
    void shouldHandleNoResourceFoundException() throws Exception {
        mockMvc.perform(get("/test/exceptions/rota-inexistente"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Rota não encontrada."))
            .andExpect(jsonPath("$.detail").value(Matchers.containsString("test/exceptions/rota-inexistente")))
            .andExpect(jsonPath("$.timestamp").value(NOW));
    }

    // HTTP 405 — verbo HTTP não suportado pela rota
    @Test
    void shouldHandleHttpRequestMethodNotSupportedException() throws Exception {
        mockMvc.perform(delete("/test/exceptions/business"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.status").value(405))
            .andExpect(jsonPath("$.message").value("Método HTTP não suportado."))
            .andExpect(jsonPath("$.detail").value(Matchers.containsString("DELETE")))
            .andExpect(jsonPath("$.timestamp").value(NOW));
    }

    // HTTP 415 — Content-Type não suportado
    @Test
    void shouldHandleHttpMediaTypeNotSupportedException() throws Exception {
        mockMvc.perform(post("/test/exceptions/validation")
                .contentType(MediaType.TEXT_PLAIN)
                .content("name"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.status").value(415))
            .andExpect(jsonPath("$.message").value("Tipo de conteúdo não suportado."))
            .andExpect(jsonPath("$.detail").value(Matchers.containsString("text/plain")))
            .andExpect(jsonPath("$.timestamp").value(NOW));
    }

    // HTTP 400 — enum inválido em @RequestParam
    @Test
    void shouldHandleMethodArgumentTypeMismatchException() throws Exception {
        mockMvc.perform(get("/test/exceptions/type-mismatch")
                .param("status", "INVALIDO"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Parâmetro inválido."))
            .andExpect(jsonPath("$.detail").value(Matchers.containsString("INVALIDO")))
            .andExpect(jsonPath("$.timestamp").value(NOW));
    }

    // HTTP 400 — JSON malformado
    @Test
    void shouldHandleHttpMessageNotReadableException() throws Exception {
        mockMvc.perform(post("/test/exceptions/unreadable")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"invalid\" \"json\" }"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Corpo da requisição inválido ou malformado."))
            .andExpect(jsonPath("$.timestamp").value(NOW));
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
            .andExpect(jsonPath("$.timestamp").value(NOW));
    }

}
