package br.com.finc2u.server.exception;

// Requisição inconsistente que só o service/entidade consegue detectar (ex.: depende do vencimento calculado pelo cartão).
// Equivale a uma falha de Bean Validation: 400, não 422.
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }

}
