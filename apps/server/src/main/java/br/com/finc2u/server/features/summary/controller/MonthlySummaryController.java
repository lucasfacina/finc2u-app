package br.com.finc2u.server.features.summary.controller;

import br.com.finc2u.server.features.summary.dto.MonthlySummaryRequest;
import br.com.finc2u.server.features.summary.form.MonthlySummaryResponse;
import br.com.finc2u.server.features.summary.service.MonthlySummaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/monthly-summary")
@RequiredArgsConstructor
public class MonthlySummaryController {

    private final MonthlySummaryService monthlySummaryService;

    @PostMapping
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
    public ResponseEntity<List<MonthlySummaryResponse>> getHistoryByUserId(@RequestParam UUID userId) {
        List<MonthlySummaryResponse> monthlySummaryResponse = monthlySummaryService.getByUser(userId).stream()
            .map(detail -> MonthlySummaryResponse.from(detail.summary(), detail.categoryTotals()))
            .toList();
        return ResponseEntity.ok(monthlySummaryResponse);
    }

    @GetMapping("/{userId}/{year}/{month}")
    public ResponseEntity<MonthlySummaryResponse> getPeriodByUserId(@PathVariable UUID userId, @PathVariable Integer year, @PathVariable Integer month) {
        var detail = monthlySummaryService.getByUserAndPeriod(userId, month, year);
        return ResponseEntity.ok(MonthlySummaryResponse.from(detail.summary(), detail.categoryTotals()));
    }

    @DeleteMapping("/{userId}/{year}/{month}")
    public ResponseEntity<Void> delete(@PathVariable UUID userId, @PathVariable Integer year, @PathVariable Integer month) {
        monthlySummaryService.delete(userId, month, year);
        return ResponseEntity.noContent().build();
    }

}
