package br.com.finc2u.server.features.recurringexpense.controller;

import br.com.finc2u.server.features.recurringexpense.dto.RecurringExpenseRequest;
import br.com.finc2u.server.features.recurringexpense.form.RecurringExpenseResponse;
import br.com.finc2u.server.features.recurringexpense.service.RecurringExpenseService;
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
@Tag(name = "Despesas recorrentes", description = "Modelos de despesa mensal (assinaturas e contas que se repetem). O " +
    "sistema gera uma despesa por mês a partir de cada modelo.")
@RequestMapping("/recurring-expenses")
@RequiredArgsConstructor
public class RecurringExpenseController {

    private final RecurringExpenseService recurringExpenseService;

    @PostMapping
    @Operation(
        operationId = "createRecurringExpense",
        summary = "Cadastra uma despesa recorrente",
        description = "Cadastra o modelo e já gera as despesas do mês corrente e do seguinte. Não gera mês passado nem " +
            "vencimento que já passou. A categoria vem por id ou por nome; o cartão é opcional."
    )
    @ApiResponse(responseCode = "201", description = "Despesa recorrente criada.")
    @ApiResponse(responseCode = "404", description = "Usuário, categoria ou cartão não encontrado.")
    public ResponseEntity<RecurringExpenseResponse> create(
        @RequestParam UUID userId,
        @Valid @RequestBody RecurringExpenseRequest request
    ) {
        var saved = recurringExpenseService.create(request.toCommand(), userId);
        return new ResponseEntity<>(RecurringExpenseResponse.from(saved), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(
        operationId = "listRecurringExpenses",
        summary = "Lista as despesas recorrentes do usuário",
        description = "Ordenadas pela descrição."
    )
    @ApiResponse(responseCode = "200", description = "Despesas recorrentes do usuário.")
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado.")
    public ResponseEntity<List<RecurringExpenseResponse>> getAllByUserId(@RequestParam UUID userId) {
        var response = recurringExpenseService.getByUser(userId)
            .stream()
            .map(RecurringExpenseResponse::from)
            .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(
        operationId = "getRecurringExpense",
        summary = "Busca uma despesa recorrente pelo id",
        description = "Devolve o modelo."
    )
    @ApiResponse(responseCode = "200", description = "Despesa recorrente encontrada.")
    @ApiResponse(responseCode = "404", description = "Despesa recorrente não encontrada.")
    public ResponseEntity<RecurringExpenseResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(RecurringExpenseResponse.from(recurringExpenseService.getById(id)));
    }

    @PutMapping("/{id}")
    @Operation(
        operationId = "updateRecurringExpense",
        summary = "Altera uma despesa recorrente",
        description = "Altera o modelo. Vale daqui para frente: as despesas já geradas não mudam."
    )
    @ApiResponse(responseCode = "200", description = "Despesa recorrente alterada.")
    @ApiResponse(responseCode = "404", description = "Despesa recorrente, categoria ou cartão não encontrado.")
    public ResponseEntity<RecurringExpenseResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody RecurringExpenseRequest request
    ) {
        var updated = recurringExpenseService.update(id, request.toCommand());
        return ResponseEntity.ok(RecurringExpenseResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(
        operationId = "deleteRecurringExpense",
        summary = "Apaga uma despesa recorrente",
        description = "Apaga o modelo. As despesas já geradas ficam, sem modelo; para parar de gerar, use o mês de " +
            "término (endMonth)."
    )
    @ApiResponse(responseCode = "204", description = "Despesa recorrente removida.")
    @ApiResponse(responseCode = "404", description = "Despesa recorrente não encontrada.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        recurringExpenseService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
