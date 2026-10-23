package br.com.finc2u.server.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
@Schema(description = "Erro padronizado devolvido pela API em qualquer falha (400, 404, 409, 422 e 500).")
public class ErrorResponse {

    @Schema(
        description = "Código HTTP do erro.",
        example = "404"
    )
    private int status;

    @Schema(
        description = "Mensagem principal do erro.",
        example = "Despesa não encontrada com o ID: 3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    private String message;

    @Schema(
        description = "Detalhe complementar do erro (por exemplo, os campos inválidos de uma validação).",
        example = "O recurso solicitado não foi encontrado."
    )
    private String detail;

    @Schema(
        description = "Momento em que o erro foi gerado.",
        example = "2026-10-23T08:56:12"
    )
    private LocalDateTime timestamp;

}
