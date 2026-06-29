package br.com.finc2u.server.features.summary.controller;

import br.com.finc2u.server.features.summary.dto.MonthlySummaryRequest;
import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.form.MonthlySummaryResponse;
import br.com.finc2u.server.features.summary.service.MonthlySummaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/monthly-summary")
@RequiredArgsConstructor
public class MonthlySummaryController {

    private final MonthlySummaryService monthlySummaryService;

    @PostMapping
    public ResponseEntity<MonthlySummaryResponse> createOrUpdate(@Valid @RequestBody MonthlySummaryRequest monthlySummaryRequest) {
        MonthlySummary monthlySummary = monthlySummaryService.calculateOrRecalculate(
                monthlySummaryRequest.userId(),
                monthlySummaryRequest.month(),
                monthlySummaryRequest.year(),
                monthlySummaryRequest.cashBalance()
        );
        return new ResponseEntity<>(MonthlySummaryResponse.from(monthlySummary), HttpStatus.CREATED);
    }

    @GetMapping("/history")
    public ResponseEntity<List<MonthlySummaryResponse>> getHistoryByUserId(@RequestParam UUID userId) {
        List<MonthlySummary> monthHistory = monthlySummaryService.getByUser(userId);
        List<MonthlySummaryResponse> monthlySummaryResponse = monthHistory.stream()
                .map(MonthlySummaryResponse::from)
                .toList();
        return ResponseEntity.ok(monthlySummaryResponse);
    }

    @GetMapping("/{userId}/{year}/{month}")
    public ResponseEntity<MonthlySummaryResponse> getPeriodByUserId(@PathVariable UUID userId, @PathVariable Integer year, @PathVariable Integer month) {
        MonthlySummary monthlySummary = monthlySummaryService.getByUserAndPeriod(userId, month, year);
        return ResponseEntity.ok(MonthlySummaryResponse.from(monthlySummary));
    }

    @DeleteMapping("/{userId}/{year}/{month}")
    public ResponseEntity<Void> delete(@PathVariable UUID userId, @PathVariable Integer year, @PathVariable Integer month) {
        monthlySummaryService.delete(userId, month, year);
        return ResponseEntity.noContent().build();
    }

}
