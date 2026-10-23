package br.com.finc2u.server.config;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ErrorResponse;
import br.com.finc2u.server.exception.InvalidRequestException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Clock clock;

    // HTTP 404
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException exception) {
        return respond(HttpStatus.NOT_FOUND, exception.getMessage(), "O recurso solicitado não foi encontrado.");
    }

    // HTTP 422
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        return respond(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage(), "Regra de negócio violada.");
    }

    // HTTP 400
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequestException(InvalidRequestException exception) {
        return respond(HttpStatus.BAD_REQUEST, exception.getMessage(), "A requisição não pôde ser processada.");
    }

    // HTTP 400
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
        var detail = "Valor inválido '" + exception.getValue() + "' para o parâmetro '" + exception.getName() + "'.";

        return respond(HttpStatus.BAD_REQUEST, "Parâmetro inválido.", detail);
    }

    // HTTP 400
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable() {
        return respond(
            HttpStatus.BAD_REQUEST,
            "Corpo da requisição inválido ou malformado.",
            "Verifique o formato e os tipos dos campos enviados."
        );
    }

    // HTTP 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        var details = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));

        return respond(HttpStatus.BAD_REQUEST, "Erro de validação.", details);
    }

    // HTTP 409
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException exception) {
        var unique = isUniqueViolation(exception);
        var message = unique
            ? "Já existe um registro com estes dados."
            : "Operação não permitida, existem registros dependentes vinculados a este recurso.";
        var detail = unique
            ? "Violação de unicidade."
            : "Violação de integridade referencial.";

        return respond(HttpStatus.CONFLICT, message, detail);
    }

    // HTTP 400
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(MissingServletRequestParameterException exception) {
        var detail = "O parâmetro '" + exception.getParameterName() + "' é obrigatório.";

        return respond(HttpStatus.BAD_REQUEST, "Parâmetro obrigatório ausente.", detail);
    }

    // HTTP 404
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException exception) {
        var detail = "Nenhum endpoint corresponde a '" + exception.getResourcePath() + "'.";

        return respond(HttpStatus.NOT_FOUND, "Rota não encontrada.", detail);
    }

    // HTTP 405
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        var supported = exception.getSupportedMethods() != null
            ? String.join(", ", exception.getSupportedMethods())
            : "-";
        var detail = "O método '" + exception.getMethod() + "' não é suportado nesta rota. Métodos aceitos: " + supported + ".";

        return respond(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP não suportado.", detail);
    }

    // HTTP 415
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception) {
        var supported = exception.getSupportedMediaTypes().stream()
            .map(MediaType::toString)
            .collect(Collectors.joining(", "));
        var detail = "O Content-Type '" + exception.getContentType() + "' não é suportado. Tipos aceitos: " + supported + ".";

        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de conteúdo não suportado.", detail);
    }

    // HTTP 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception exception) {
        log.error("Erro inesperado ao processar a requisição.", exception);

        return respond(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Erro interno no servidor.",
            "Ocorreu um erro inesperado. Tente novamente mais tarde."
        );
    }

    // Monta a resposta de erro padrão; o instante vem do `Clock`, para a saída ser reproduzível nos testes.
    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String message, String detail) {
        var error = new ErrorResponse(status.value(), message, detail, LocalDateTime.now(clock));

        return new ResponseEntity<>(error, status);
    }

    // Unicidade × dependência: percorre a cadeia de causas, pois o Spring pode traduzir a mesma violação de formas diferentes.
    private static boolean isUniqueViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) return true;

            if (cause instanceof ConstraintViolationException violation
                && violation.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) {
                return true;
            }
        }

        return false;
    }

}
