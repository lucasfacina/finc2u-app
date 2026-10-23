package br.com.finc2u.server.features.summary.controller;

import br.com.finc2u.server.features.summary.dto.MonthlySummaryRequest;
import br.com.finc2u.server.features.summary.form.MonthlySummaryResponse;
import br.com.finc2u.server.features.summary.service.MonthlySummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Resumo mensal", description = "Resumo financeiro do mês: renda, despesas, saldo e totais por categoria. O " +
    "saldo de um mês é o carryover do seguinte.")
@RequestMapping("/monthly-summary")
@RequiredArgsConstructor
public class MonthlySummaryController {

    private final MonthlySummaryService monthlySummaryService;

    @PostMapping
    @Operation(
        operationId = "calculateMonthlySummary",
        summary = "Calcula (ou recalcula) o resumo de um mês",
        description = "Calcula o mês pedido e recalcula, em ordem, só os resumos que já existem nos meses seguintes (o " +
            "saldo de um mês é o carryover do próximo). Não cria resumo de mês que não foi pedido."
    )
    @ApiResponse(responseCode = "200", description = "Resumo recalculado.")
    @ApiResponse(responseCode = "201", description = "Resumo criado.")
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado.")
    public ResponseEntity<MonthlySummaryResponse> createOrUpdate(@Valid @RequestBody MonthlySummaryRequest monthlySummaryRequest) {
        var result = monthlySummaryService.recalculateFrom(
            monthlySummaryRequest.userId(),
            YearMonth.of(monthlySummaryRequest.year(), monthlySummaryRequest.month())
        );

        return new ResponseEntity<>(MonthlySummaryResponse.from(result.summary(), result.categoryTotals()),
            result.created()
                ? HttpStatus.CREATED
                : HttpStatus.OK
        );
    }

    @GetMapping("/history")
    @Operation(
        operationId = "getMonthlySummaryHistory",
        summary = "Lista o histórico de resumos do usuário",
        description = "Todos os resumos do usuário, com os totais por categoria."
    )
    public ResponseEntity<List<MonthlySummaryResponse>> getHistoryByUserId(@RequestParam UUID userId) {
        var monthlySummaryResponse = monthlySummaryService.getByUser(userId).stream()
            .map(MonthlySummaryResponse::from)
            .toList();
        return ResponseEntity.ok(monthlySummaryResponse);
    }

    @GetMapping("/{userId}/{year}/{month}")
    @Operation(
        operationId = "getMonthlySummary",
        summary = "Busca o resumo de um mês",
        description = "Só leitura: quem calcula é o POST."
    )
    @ApiResponse(responseCode = "200", description = "Resumo encontrado.")
    @ApiResponse(responseCode = "404", description = "Não há resumo para o período.")
    public ResponseEntity<MonthlySummaryResponse> getPeriodByUserId(@PathVariable UUID userId, @PathVariable Integer year, @PathVariable Integer month) {
        var detail = monthlySummaryService.getByUserAndPeriod(userId, month, year);
        return ResponseEntity.ok(MonthlySummaryResponse.from(detail));
    }

    @DeleteMapping("/{userId}/{year}/{month}")
    @Operation(
        operationId = "deleteMonthlySummary",
        summary = "Apaga o resumo de um mês",
        description = "Apaga o resumo e recalcula os seguintes: o do mês imediato, se existir, perde o carryover e fica " +
            "com previousMonthMissing."
    )
    @ApiResponse(responseCode = "204", description = "Resumo removido.")
    @ApiResponse(responseCode = "404", description = "Não há resumo para o período.")
    public ResponseEntity<Void> delete(@PathVariable UUID userId, @PathVariable Integer year, @PathVariable Integer month) {
        monthlySummaryService.delete(userId, month, year);
        return ResponseEntity.noContent().build();
    }

}
