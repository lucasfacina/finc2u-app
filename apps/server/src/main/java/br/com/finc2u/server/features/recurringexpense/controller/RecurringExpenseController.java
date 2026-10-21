package br.com.finc2u.server.features.recurringexpense.controller;

import br.com.finc2u.server.features.recurringexpense.dto.RecurringExpenseRequest;
import br.com.finc2u.server.features.recurringexpense.form.RecurringExpenseResponse;
import br.com.finc2u.server.features.recurringexpense.service.RecurringExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/recurring-expenses")
@RequiredArgsConstructor
public class RecurringExpenseController {

    private final RecurringExpenseService recurringExpenseService;

    @PostMapping
    public ResponseEntity<RecurringExpenseResponse> create(
        @RequestParam UUID userId,
        @Valid @RequestBody RecurringExpenseRequest request
    ) {
        var saved = recurringExpenseService.create(request.toCommand(), userId);
        return new ResponseEntity<>(RecurringExpenseResponse.from(saved), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<RecurringExpenseResponse>> getAllByUserId(@RequestParam UUID userId) {
        var response = recurringExpenseService.getByUser(userId)
            .stream()
            .map(RecurringExpenseResponse::from)
            .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecurringExpenseResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(RecurringExpenseResponse.from(recurringExpenseService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecurringExpenseResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody RecurringExpenseRequest request
    ) {
        var updated = recurringExpenseService.update(id, request.toCommand());
        return ResponseEntity.ok(RecurringExpenseResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        recurringExpenseService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
