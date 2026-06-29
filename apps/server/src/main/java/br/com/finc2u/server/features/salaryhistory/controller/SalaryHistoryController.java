package br.com.finc2u.server.features.salaryhistory.controller;

import br.com.finc2u.server.features.salaryhistory.dto.SalaryHistoryRequest;
import br.com.finc2u.server.features.salaryhistory.entity.SalaryHistory;
import br.com.finc2u.server.features.salaryhistory.form.SalaryHistoryResponse;
import br.com.finc2u.server.features.salaryhistory.service.SalaryHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/salary-history")
@RequiredArgsConstructor
public class SalaryHistoryController {

    private final SalaryHistoryService salaryHistoryService;

    @PostMapping
    public ResponseEntity<SalaryHistoryResponse> create(@RequestBody @Valid SalaryHistoryRequest salaryHistoryRequest) {
        SalaryHistory savedHistory = salaryHistoryService.create(salaryHistoryRequest.toEntity(), salaryHistoryRequest.userId());
        return new ResponseEntity<>(SalaryHistoryResponse.from(savedHistory), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<SalaryHistoryResponse>> getByUserId(@RequestParam UUID userId) {
        List<SalaryHistoryResponse> extraResponse = salaryHistoryService.getByUser(userId)
                .stream()
                .map(SalaryHistoryResponse::from)
                .toList();
        return ResponseEntity.ok(extraResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SalaryHistoryResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(SalaryHistoryResponse.from(salaryHistoryService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SalaryHistoryResponse> update(@PathVariable UUID id, @RequestBody @Valid SalaryHistoryRequest salaryHistoryRequest) {
        SalaryHistory updatedHistory = salaryHistoryService.update(id, salaryHistoryRequest.toEntity());
        return ResponseEntity.ok(SalaryHistoryResponse.from(updatedHistory));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        salaryHistoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
