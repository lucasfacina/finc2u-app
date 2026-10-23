package br.com.finc2u.server.features.salaryhistory.controller;

import br.com.finc2u.server.features.salaryhistory.dto.SalaryHistoryRequest;
import br.com.finc2u.server.features.salaryhistory.form.SalaryHistoryResponse;
import br.com.finc2u.server.features.salaryhistory.service.SalaryHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Histórico de salários", description = "Vigências de salário do usuário. Cada registro vale a partir da sua " +
    "data; recalcular um mês antigo usa o salário que valia naquele " +
    "período.")
@RequestMapping("/salary-history")
@RequiredArgsConstructor
public class SalaryHistoryController {

    private final SalaryHistoryService salaryHistoryService;

    @PostMapping
    @Operation(
        operationId = "createSalaryHistory",
        summary = "Registra uma vigência de salário",
        description = "Registra o salário a partir de effectiveFrom."
    )
    @ApiResponse(responseCode = "201", description = "Vigência criada.")
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado.")
    @ApiResponse(responseCode = "422", description = "O usuário já tem uma vigência nessa data.")
    public ResponseEntity<SalaryHistoryResponse> create(@RequestBody @Valid SalaryHistoryRequest salaryHistoryRequest) {
        var savedHistory = salaryHistoryService.create(salaryHistoryRequest.toEntity(), salaryHistoryRequest.userId());
        return new ResponseEntity<>(SalaryHistoryResponse.from(savedHistory), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(
        operationId = "listSalaryHistory",
        summary = "Lista as vigências de salário do usuário",
        description = "Da mais recente para a mais antiga. Sem nenhuma, devolve lista vazia."
    )
    public ResponseEntity<List<SalaryHistoryResponse>> getByUserId(@RequestParam UUID userId) {
        var extraIncomeResponse = salaryHistoryService.getByUser(userId)
            .stream()
            .map(SalaryHistoryResponse::from)
            .toList();
        return ResponseEntity.ok(extraIncomeResponse);
    }

    @GetMapping("/{id}")
    @Operation(
        operationId = "getSalaryHistory",
        summary = "Busca uma vigência pelo id",
        description = "Devolve a vigência."
    )
    @ApiResponse(responseCode = "200", description = "Vigência encontrada.")
    @ApiResponse(responseCode = "404", description = "Vigência não encontrada.")
    public ResponseEntity<SalaryHistoryResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(SalaryHistoryResponse.from(salaryHistoryService.getById(id)));
    }

    @PutMapping("/{id}")
    @Operation(
        operationId = "updateSalaryHistory",
        summary = "Altera uma vigência de salário",
        description = "Altera o salário e a data de vigência."
    )
    @ApiResponse(responseCode = "200", description = "Vigência alterada.")
    @ApiResponse(responseCode = "404", description = "Vigência não encontrada.")
    @ApiResponse(responseCode = "422", description = "Outra vigência do usuário já tem essa data.")
    public ResponseEntity<SalaryHistoryResponse> update(@PathVariable UUID id, @RequestBody @Valid SalaryHistoryRequest salaryHistoryRequest) {
        var updatedHistory = salaryHistoryService.update(id, salaryHistoryRequest.toEntity());
        return ResponseEntity.ok(SalaryHistoryResponse.from(updatedHistory));
    }

    @DeleteMapping("/{id}")
    @Operation(
        operationId = "deleteSalaryHistory",
        summary = "Apaga uma vigência de salário",
        description = "Apaga a vigência."
    )
    @ApiResponse(responseCode = "204", description = "Vigência removida.")
    @ApiResponse(responseCode = "404", description = "Vigência não encontrada.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        salaryHistoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
