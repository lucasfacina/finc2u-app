package br.com.finc2u.server.config;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ErrorResponse;
import br.com.finc2u.server.exception.InvalidRequestException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
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

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    // HTTP 404
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException exception) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ErrorResponse error = new ErrorResponse(
            status.value(),
            exception.getMessage(),
            "O recurso solicitado não foi encontrado.",
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, status);
    }

    // HTTP 422
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        HttpStatus status = HttpStatus.UNPROCESSABLE_CONTENT;
        ErrorResponse error = new ErrorResponse(
            status.value(),
            exception.getMessage(),
            "Regra de negócio violada.",
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, status);
    }

    // HTTP 400
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequestException(InvalidRequestException exception) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ErrorResponse error = new ErrorResponse(
            status.value(),
            exception.getMessage(),
            "A requisição não pôde ser processada.",
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, status);
    }

    // HTTP 400
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String detail = "Valor inválido '" + exception.getValue() + "' para o parâmetro '" + exception.getName() + "'.";
        ErrorResponse error = new ErrorResponse(
            status.value(),
            "Parâmetro inválido.",
            detail,
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, status);
    }

    // HTTP 400
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable() {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ErrorResponse error = new ErrorResponse(
            status.value(),
            "Corpo da requisição inválido ou malformado.",
            "Verifique o formato e os tipos dos campos enviados.",
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, status);
    }

    // HTTP 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String details = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));

        ErrorResponse error = new ErrorResponse(
            status.value(),
            "Erro de validação.",
            details,
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, status);
    }

    // HTTP 409
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException exception) {
        HttpStatus status = HttpStatus.CONFLICT;
        boolean unique = isUniqueViolation(exception);
        ErrorResponse error = new ErrorResponse(
            status.value(),
            unique
                ? "Já existe um registro com estes dados."
                : "Operação não permitida, existem registros dependentes vinculados a este recurso.",
            unique
                ? "Violação de unicidade."
                : "Violação de integridade referencial.",
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, status);
    }

    // HTTP 400
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(MissingServletRequestParameterException exception) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ErrorResponse error = new ErrorResponse(
            status.value(),
            "Parâmetro obrigatório ausente.",
            "O parâmetro '" + exception.getParameterName() + "' é obrigatório.",
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, status);
    }

    // HTTP 404
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException exception) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ErrorResponse error = new ErrorResponse(
            status.value(),
            "Rota não encontrada.",
            "Nenhum endpoint corresponde a '" + exception.getResourcePath() + "'.",
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, status);
    }

    // HTTP 405
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        HttpStatus status = HttpStatus.METHOD_NOT_ALLOWED;
        String supported = exception.getSupportedMethods() != null
            ? String.join(", ", exception.getSupportedMethods())
            : "-";
        ErrorResponse error = new ErrorResponse(
            status.value(),
            "Método HTTP não suportado.",
            "O método '" + exception.getMethod() + "' não é suportado nesta rota. Métodos aceitos: " + supported + ".",
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, status);
    }

    // HTTP 415
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception) {
        HttpStatus status = HttpStatus.UNSUPPORTED_MEDIA_TYPE;
        String supported = exception.getSupportedMediaTypes().stream()
            .map(MediaType::toString)
            .collect(Collectors.joining(", "));
        ErrorResponse error = new ErrorResponse(
            status.value(),
            "Tipo de conteúdo não suportado.",
            "O Content-Type '" + exception.getContentType() + "' não é suportado. Tipos aceitos: " + supported + ".",
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, status);
    }

    // HTTP 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception exception) {
        log.error("Erro inesperado ao processar a requisição.", exception);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorResponse error = new ErrorResponse(
            status.value(),
            "Erro interno no servidor.",
            "Ocorreu um erro inesperado. Tente novamente mais tarde.",
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, status);
    }

    // Unicidade × dependência: percorre a cadeia de causas, pois o Spring pode traduzir a mesma violação de formas diferentes.
    private static boolean isUniqueViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
            if (cause instanceof ConstraintViolationException violation
                && violation.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) {
                return true;
            }
        }
        return false;
    }

}
