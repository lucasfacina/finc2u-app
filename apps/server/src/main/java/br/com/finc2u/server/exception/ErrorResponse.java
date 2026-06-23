package br.com.finc2u.server.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
public class ErrorResponse {

    private int status;
    private String message;
    private String detail;
    private LocalDateTime timestamp;

}
