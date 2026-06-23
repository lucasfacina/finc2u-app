package br.com.finc2u.server.config;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ErrorResponse;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

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
        HttpStatus status = HttpStatus.UNPROCESSABLE_ENTITY;
        ErrorResponse error = new ErrorResponse(
                status.value(),
                exception.getMessage(),
                "Regra de negócio violada.",
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
        ErrorResponse error = new ErrorResponse(
                status.value(),
                "Operação não permitida, existem registros dependentes vinculados a este recurso.",
                exception.getRootCause() != null ? exception.getRootCause().getMessage() : exception.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, status);
    }

    // HTTP 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception exception) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorResponse error = new ErrorResponse(
                status.value(),
                "Erro interno no servidor.",
                exception.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, status);
    }

}
