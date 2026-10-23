package br.com.finc2u.server.features.extraincome.controller;

import br.com.finc2u.server.features.extraincome.dto.ExtraIncomeRequest;
import br.com.finc2u.server.features.extraincome.form.ExtraIncomeResponse;
import br.com.finc2u.server.features.extraincome.service.ExtraIncomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Ganhos extras", description = "Entradas além do salário, somadas como renda no resumo mensal.")
@RequestMapping("/extra-incomes")
@RequiredArgsConstructor
public class ExtraIncomeController {

    private final ExtraIncomeService extraIncomeService;

    @PostMapping
    @Operation(
        operationId = "createExtraIncome",
        summary = "Registra um ganho extra",
        description = "Registra uma entrada além do salário na data informada."
    )
    @ApiResponse(responseCode = "201", description = "Ganho extra criado.")
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado.")
    public ResponseEntity<ExtraIncomeResponse> create(
        @RequestParam UUID userId,
        @Valid @RequestBody ExtraIncomeRequest extraIncomeRequest
    ) {
        var savedExtraIncome = extraIncomeService.create(extraIncomeRequest.toEntity(), userId);
        return new ResponseEntity<>(ExtraIncomeResponse.from(savedExtraIncome), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(
        operationId = "listExtraIncomes",
        summary = "Lista os ganhos extras do usuário",
        description = "Todos os ganhos extras do usuário."
    )
    @ApiResponse(responseCode = "200", description = "Ganhos extras do usuário.")
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado.")
    public ResponseEntity<List<ExtraIncomeResponse>> getAllByUserId(@RequestParam UUID userId) {
        var extraIncomeResponse = extraIncomeService.getByUser(userId)
            .stream()
            .map(ExtraIncomeResponse::from)
            .toList();
        return ResponseEntity.ok(extraIncomeResponse);
    }

    @GetMapping("/period")
    @Operation(
        operationId = "listExtraIncomesByPeriod",
        summary = "Lista os ganhos extras de um período",
        description = "Ganhos com data entre startDate e endDate, inclusive. A data inicial não pode ser posterior à " +
            "final."
    )
    @ApiResponse(responseCode = "200", description = "Ganhos extras do período.")
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado.")
    public ResponseEntity<List<ExtraIncomeResponse>> getByUserIdAndPeriod(
        @RequestParam UUID userId,
        @RequestParam LocalDate startDate,
        @RequestParam LocalDate endDate
    ) {
        var response = extraIncomeService.getByUserAndPeriod(userId, startDate, endDate)
            .stream()
            .map(ExtraIncomeResponse::from)
            .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(
        operationId = "getExtraIncome",
        summary = "Busca um ganho extra pelo id",
        description = "Devolve o ganho extra."
    )
    @ApiResponse(responseCode = "200", description = "Ganho extra encontrado.")
    @ApiResponse(responseCode = "404", description = "Ganho extra não encontrado.")
    public ResponseEntity<ExtraIncomeResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ExtraIncomeResponse.from(extraIncomeService.getById(id)));
    }

    @PutMapping("/{id}")
    @Operation(
        operationId = "updateExtraIncome",
        summary = "Altera um ganho extra",
        description = "Altera descrição, valor e data."
    )
    @ApiResponse(responseCode = "200", description = "Ganho extra alterado.")
    @ApiResponse(responseCode = "404", description = "Ganho extra não encontrado.")
    public ResponseEntity<ExtraIncomeResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody ExtraIncomeRequest extraIncomeRequest
    ) {
        var updatedExtraIncome = extraIncomeService.update(id, extraIncomeRequest.toEntity());
        return ResponseEntity.ok(ExtraIncomeResponse.from(updatedExtraIncome));
    }

    @DeleteMapping("/{id}")
    @Operation(
        operationId = "deleteExtraIncome",
        summary = "Apaga um ganho extra",
        description = "Apaga o ganho extra."
    )
    @ApiResponse(responseCode = "204", description = "Ganho extra removido.")
    @ApiResponse(responseCode = "404", description = "Ganho extra não encontrado.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        extraIncomeService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
