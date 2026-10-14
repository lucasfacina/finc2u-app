package br.com.finc2u.server.features.extraincome.controller;

import br.com.finc2u.server.features.extraincome.dto.ExtraIncomeRequest;
import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import br.com.finc2u.server.features.extraincome.form.ExtraIncomeResponse;
import br.com.finc2u.server.features.extraincome.service.ExtraIncomeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/extra-incomes")
@RequiredArgsConstructor
public class ExtraIncomeController {

    private final ExtraIncomeService extraIncomeService;

    @PostMapping
    public ResponseEntity<ExtraIncomeResponse> create(
            @RequestParam UUID userId,
            @Valid @RequestBody ExtraIncomeRequest extraIncomeRequest
    ) {
        ExtraIncome savedExtraIncome = extraIncomeService.create(extraIncomeRequest.toEntity(), userId);
        return new ResponseEntity<>(ExtraIncomeResponse.from(savedExtraIncome), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ExtraIncomeResponse>> getAllByUserId(@RequestParam UUID userId) {
        List<ExtraIncomeResponse> extraIncomeResponse = extraIncomeService.getByUser(userId)
                .stream()
                .map(ExtraIncomeResponse::from)
                .toList();
        return ResponseEntity.ok(extraIncomeResponse);
    }

    @GetMapping("/period")
    public ResponseEntity<List<ExtraIncomeResponse>> getByUserIdAndPeriod(
            @RequestParam UUID userId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        List<ExtraIncomeResponse> response = extraIncomeService.getByUserAndPeriod(userId, startDate, endDate)
                .stream()
                .map(ExtraIncomeResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExtraIncomeResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ExtraIncomeResponse.from(extraIncomeService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExtraIncomeResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ExtraIncomeRequest extraIncomeRequest
    ) {
        ExtraIncome updatedExtraIncome = extraIncomeService.update(id, extraIncomeRequest.toEntity());
        return ResponseEntity.ok(ExtraIncomeResponse.from(updatedExtraIncome));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        extraIncomeService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
