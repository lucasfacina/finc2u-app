package br.com.finc2u.server.config;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;

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

    // HTTP 409
    @GetMapping("/data-integrity")
    public void throwDataIntegrity() {
        throw new DataIntegrityViolationException("Violação de integridade referencial.");
    }

    // HTTP 500
    @GetMapping("/generic")
    public void throwGenericException() {
        throw new RuntimeException("Erro inesperado");
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
