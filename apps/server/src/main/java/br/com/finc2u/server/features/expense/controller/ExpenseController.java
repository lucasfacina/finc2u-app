package br.com.finc2u.server.features.expense.controller;

import br.com.finc2u.server.features.expense.dto.ExpenseFilterRequest;
import br.com.finc2u.server.features.expense.dto.ExpenseRequest;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.form.ExpenseResponse;
import br.com.finc2u.server.features.expense.projection.FutureInstallmentProjection;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@Tag(name = "Despesas", description = "Lançamento, consulta, alteração e exclusão das despesas do usuário, inclusive as " +
    "compras parceladas (uma linha por parcela).")
@RequestMapping("/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;
    private final FutureInstallmentProjection futureInstallmentProjection;
    private final Clock clock;

    @PostMapping
    @Operation(
        operationId = "createExpense",
        summary = "Cria uma despesa",
        description = "Cria a despesa do usuário. Cartão e categoria podem ser informados por id ou por nome (a " +
            "categoria por nome é criada se ainda não existir). Sem vencimento, ele é calculado pela fatura " +
            "do cartão; sem status, a despesa nasce PENDING. Em uma compra PARCELED, grava as parcelas da " +
            "informada até o total e devolve só a informada."
    )
    @ApiResponse(responseCode = "201", description = "Despesa criada (em compra parcelada, a parcela informada).")
    @ApiResponse(responseCode = "404", description = "Usuário, cartão ou categoria não encontrado.")
    @ApiResponse(responseCode = "422", description = "Cartão expirado na data da compra ou regra da despesa violada " +
        "(vencimento ausente sem cartão, parcelas inválidas, data de " +
        "pagamento incoerente).")
    public ResponseEntity<ExpenseResponse> create(@RequestParam UUID userId, @Valid @RequestBody ExpenseRequest expenseRequest) {
        var savedExpense = expenseService.create(userId, expenseRequest.toCommand());
        return new ResponseEntity<>(toResponse(savedExpense), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(
        operationId = "listExpenses",
        summary = "Lista as despesas do usuário",
        description = "Todas as despesas do usuário, sem filtro. Para filtrar, use /expenses/filters."
    )
    @ApiResponse(responseCode = "200", description = "Despesas do usuário.")
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado.")
    public ResponseEntity<List<ExpenseResponse>> getAllByUserId(@RequestParam UUID userId) {
        var today = LocalDate.now(clock);
        var expenses = expenseService.getByUser(userId);

        return ResponseEntity.ok(ExpenseResponse.listOf(expenses, today));
    }

    @GetMapping("/filters")
    @Operation(
        operationId = "filterExpenses",
        summary = "Filtra as despesas do usuário",
        description = "Filtra no banco por mês e ano, status, tipo, cartão, categoria, faixa de preço e atraso " +
            "(overdue=true devolve só as pendentes já vencidas). O mês exige o ano e o preço mínimo não pode " +
            "passar do máximo."
    )
    @ApiResponse(responseCode = "200", description = "Despesas que atendem ao filtro.")
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado.")
    public ResponseEntity<List<ExpenseResponse>> getByFilters(@RequestParam UUID userId, @Valid @ParameterObject ExpenseFilterRequest filter) {
        var today = LocalDate.now(clock);
        var expenses = expenseService.getWithFilters(userId, filter.toFilter());

        return ResponseEntity.ok(ExpenseResponse.listOf(expenses, today));
    }

    @GetMapping("/{id}")
    @Operation(
        operationId = "getExpense",
        summary = "Busca uma despesa pelo id",
        description = "Devolve a despesa; se for uma compra parcelada, a parcela correspondente."
    )
    @ApiResponse(responseCode = "200", description = "Despesa encontrada.")
    @ApiResponse(responseCode = "404", description = "Despesa não encontrada.")
    public ResponseEntity<ExpenseResponse> getById(@PathVariable UUID id) {
        var expense = expenseService.getById(id);
        return ResponseEntity.ok(toResponse(expense));
    }

    @GetMapping("/installment-groups/{groupId}")
    @Operation(
        operationId = "getInstallmentGroup",
        summary = "Lista as parcelas de uma compra",
        description = "Todas as parcelas do grupo informado, da menor para a maior."
    )
    @ApiResponse(responseCode = "200", description = "Parcelas da compra.")
    @ApiResponse(responseCode = "404", description = "Nenhuma compra parcelada com esse grupo.")
    public ResponseEntity<List<ExpenseResponse>> getInstallmentGroup(@PathVariable UUID groupId) {
        var today = LocalDate.now(clock);
        var installments = expenseService.getByInstallmentGroup(groupId);

        return ResponseEntity.ok(ExpenseResponse.listOf(installments, today));
    }

    @GetMapping("/projections")
    @Operation(
        operationId = "projectInstallments",
        summary = "Projeta as parcelas futuras",
        description = "Parcelas PARCELED e PENDING com vencimento depois de hoje, agrupadas por mês (chave no formato " +
            "AAAA-MM)."
    )
    public ResponseEntity<Map<YearMonth, List<ExpenseResponse>>> getProjectionsByUserId(@RequestParam UUID userId) {
        var today = LocalDate.now(clock);
        var projections = futureInstallmentProjection.getProjectionsByUser(userId, today);

        return ResponseEntity.ok(ExpenseResponse.byMonth(projections, today));
    }

    @PutMapping("/{id}")
    @Operation(
        operationId = "updateExpense",
        summary = "Altera uma despesa",
        description = "Troca cartão, categoria e os campos editáveis e normaliza de novo vencimento, status e parcelas. " +
            "A validade do cartão só é conferida se a data da compra for informada."
    )
    @ApiResponse(responseCode = "200", description = "Despesa alterada.")
    @ApiResponse(responseCode = "404", description = "Despesa, cartão ou categoria não encontrado.")
    @ApiResponse(responseCode = "422", description = "Cartão expirado ou regra da despesa violada.")
    public ResponseEntity<ExpenseResponse> update(@PathVariable UUID id, @Valid @RequestBody ExpenseRequest expenseRequest) {
        var updatedExpense = expenseService.update(id, expenseRequest.toCommand());
        return ResponseEntity.ok(toResponse(updatedExpense));
    }

    @PatchMapping("/{id}/status")
    @Operation(
        operationId = "updateExpenseStatus",
        summary = "Muda o status de pagamento",
        description = "PAID sem data usa hoje; PENDING limpa a data. O atraso não é um status: é derivado (campo " +
            "overdue)."
    )
    @ApiResponse(responseCode = "200", description = "Status de pagamento alterado.")
    @ApiResponse(responseCode = "404", description = "Despesa não encontrada.")
    @ApiResponse(responseCode = "422", description = "Data de pagamento informada com PENDING ou posterior a hoje.")
    public ResponseEntity<ExpenseResponse> updateStatus(@PathVariable UUID id, @RequestParam PaymentStatus status,
                                                        @RequestParam(required = false) LocalDate paidAt) {
        var updatedExpenseStatus = expenseService.updateStatus(id, status, paidAt);
        return ResponseEntity.ok(toResponse(updatedExpenseStatus));
    }

    @DeleteMapping("/installment-groups/{groupId}")
    @Operation(
        operationId = "deleteInstallmentGroup",
        summary = "Apaga uma compra parcelada inteira",
        description = "Remove todas as parcelas do grupo."
    )
    @ApiResponse(responseCode = "204", description = "Compra removida.")
    @ApiResponse(responseCode = "404", description = "Nenhuma compra parcelada com esse grupo.")
    public ResponseEntity<Void> deleteInstallmentGroup(@PathVariable UUID groupId) {
        expenseService.deleteInstallmentGroup(groupId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(
        operationId = "deleteExpense",
        summary = "Apaga uma despesa",
        description = "Apaga a despesa (uma parcela, se for parcelada). Se ela foi gerada por uma despesa recorrente, o " +
            "vencimento passa a ser pulado e ela não volta."
    )
    @ApiResponse(responseCode = "204", description = "Despesa removida.")
    @ApiResponse(responseCode = "404", description = "Despesa não encontrada.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        expenseService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private ExpenseResponse toResponse(Expense expense) {
        return ExpenseResponse.from(expense, LocalDate.now(clock));
    }

}
