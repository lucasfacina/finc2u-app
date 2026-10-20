package br.com.finc2u.server.config;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.InvalidRequestException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.UUID;

@RestController
@RequestMapping("/test/exceptions")
public class ExceptionTestController {

    // HTTP 404
    @GetMapping("/resource-not-found")
    public void throwResourceNotFoundException() {
        throw new ResourceNotFoundException("Recurso não encontrado.");
    }

    // HTTP 422
    @GetMapping("/business")
    public void throwBusinessException() {
        throw new BusinessException("Erro de negócio.");
    }

    // HTTP 400
    @GetMapping("/invalid-request")
    public void throwInvalidRequestException() {
        throw new InvalidRequestException("Requisição inválida.");
    }

    // HTTP 409
    @GetMapping("/data-integrity")
    public void throwDataIntegrity() {
        throw new DataIntegrityViolationException("Violação de integridade referencial.");
    }

    // HTTP 409 — violação de unicidade (ex.: e-mail duplicado numa corrida entre duas requisições)
    @GetMapping("/data-integrity-unique")
    public void throwDataIntegrityUnique() {
        throw new DataIntegrityViolationException("could not execute statement",
            new ConstraintViolationException("duplicate", new SQLException(), ConstraintKind.UNIQUE, "uk_user_email"));
    }

    // HTTP 409 — a mesma violação, traduzida pelo Spring como DuplicateKeyException
    @GetMapping("/data-integrity-duplicate-key")
    public void throwDuplicateKey() {
        throw new DuplicateKeyException("duplicate key value violates unique constraint uk_user_email");
    }

    // HTTP 409 — dependente vinculado (chave estrangeira)
    @GetMapping("/data-integrity-foreign-key")
    public void throwDataIntegrityForeignKey() {
        throw new DataIntegrityViolationException("could not execute statement",
            new ConstraintViolationException("fk", new SQLException(), ConstraintKind.FOREIGN_KEY, "fk_expense_card"));
    }

    // HTTP 500 — a mensagem simula um detalhe interno que não pode chegar ao cliente
    @GetMapping("/generic")
    public void throwGenericException() {
        throw new RuntimeException("Falha interna: conexão recusada em tb_expense.");
    }

    // HTTP 400 — parâmetro obrigatório ausente
    @GetMapping("/missing-param")
    public void throwMissingParameter(@RequestParam UUID userId) {
    }

    // HTTP 400 — enum inválido em @RequestParam
    @GetMapping("/type-mismatch")
    public void throwTypeMismatch(@RequestParam PaymentStatus status) {
    }

    // HTTP 400 — JSON malformado (corpo inválido)
    @PostMapping("/unreadable")
    public void throwUnreadable(@RequestBody TestRequest request) {
    }

    // HTTP 400
    @PostMapping("/validation")
    public void throwValidationException(@Valid @RequestBody TestRequest request) {
    }

    @Getter
    @Setter
    public static class TestRequest {
        @NotBlank(message = "Nome é obrigatório.")
        private String name;
    }

}
