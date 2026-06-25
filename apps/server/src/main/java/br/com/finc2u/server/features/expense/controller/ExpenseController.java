package br.com.finc2u.server.features.expense.controller;

import br.com.finc2u.server.features.expense.dto.ExpenseRequest;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.filter.ExpenseFilter;
import br.com.finc2u.server.features.expense.form.ExpenseResponse;
import br.com.finc2u.server.features.expense.projection.FutureInstallmentProjection;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;
    private final FutureInstallmentProjection futureInstallmentProjection;

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@Valid @RequestBody ExpenseRequest expenseRequest) {
        Expense savedExpense = expenseService.create(
                expenseRequest.toEntity(),
                expenseRequest.userId(),
                expenseRequest.cardAccountId(),
                expenseRequest.purchaseDate(),
                expenseRequest.tagIds()
        );
        return new ResponseEntity<>(ExpenseResponse.from(savedExpense), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ExpenseResponse>> getByUserId(@RequestParam UUID userId) {
        List<ExpenseResponse> expenseResponse = expenseService.getByUser(userId)
                .stream()
                .map(ExpenseResponse::from)
                .toList();
        return ResponseEntity.ok(expenseResponse);
    }

    @GetMapping("/filters")
    public ResponseEntity<List<ExpenseResponse>> getByFilters(@RequestParam UUID userId, ExpenseFilter filter) {
        List<Expense> expenses = expenseService.getWithFilters(userId, filter);
        return ResponseEntity.ok(expenses.stream().map(ExpenseResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponse> getById(@PathVariable UUID id) {
        Expense expense = expenseService.getById(id);
        return ResponseEntity.ok(ExpenseResponse.from(expense));
    }

    @GetMapping("/projections")
    public ResponseEntity<Map<YearMonth, List<ExpenseResponse>>> getProjectionsByUserId(@RequestParam UUID userId) {
        Map<YearMonth, List<ExpenseResponse>> expenseResponse = futureInstallmentProjection.getProjectionsByUser(userId)
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue()
                                .stream()
                                .map(ExpenseResponse::from)
                                .toList()
                ));
        return ResponseEntity.ok(expenseResponse);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExpenseResponse> update(@PathVariable UUID id, @Valid @RequestBody ExpenseRequest expenseRequest) {
        Expense updatedExpense = expenseService.update(
                id,
                expenseRequest.toEntity(),
                expenseRequest.cardAccountId(),
                expenseRequest.purchaseDate(),
                expenseRequest.tagIds()
        );
        return ResponseEntity.ok(ExpenseResponse.from(updatedExpense));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ExpenseResponse> updateStatus(@PathVariable UUID id, @RequestParam PaymentStatus  status) {
        Expense updatedExpenseStatus = expenseService.updateStatus(id, status);
        return ResponseEntity.ok(ExpenseResponse.from(updatedExpenseStatus));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        expenseService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
