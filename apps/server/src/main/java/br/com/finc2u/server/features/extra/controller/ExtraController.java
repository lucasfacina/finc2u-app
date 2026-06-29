package br.com.finc2u.server.features.extra.controller;

import br.com.finc2u.server.features.extra.dto.ExtraRequest;
import br.com.finc2u.server.features.extra.entity.Extra;
import br.com.finc2u.server.features.extra.form.ExtraResponse;
import br.com.finc2u.server.features.extra.service.ExtraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/extras")
@RequiredArgsConstructor
public class ExtraController {

    private final ExtraService extraService;

    @PostMapping
    public ResponseEntity<ExtraResponse> create(@RequestParam UUID userId, @Valid @RequestBody ExtraRequest extraRequest) {
        Extra savedExtra = extraService.create(extraRequest.toEntity(), userId);
        return new ResponseEntity<>(ExtraResponse.from(savedExtra), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ExtraResponse>> getAllByUserId(@RequestParam UUID userId) {
        List<ExtraResponse> extraResponse = extraService.getByUser(userId)
                .stream()
                .map(ExtraResponse::from)
                .toList();
        return ResponseEntity.ok(extraResponse);
    }

    @GetMapping("/period")
    public ResponseEntity<List<ExtraResponse>> getByUserIdAndPeriod(@RequestParam UUID userId, @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        List<ExtraResponse> response = extraService.getByUserAndPeriod(userId, startDate, endDate)
                .stream()
                .map(ExtraResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExtraResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ExtraResponse.from(extraService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExtraResponse> update(@PathVariable UUID id, @Valid @RequestBody ExtraRequest extraRequest) {
        Extra updatedExtra = extraService.update(id, extraRequest.toEntity());
        return ResponseEntity.ok(ExtraResponse.from(updatedExtra));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        extraService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
