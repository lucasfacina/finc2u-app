package br.com.finc2u.server.features.card.controller;

import br.com.finc2u.server.features.card.dto.CardAccountRequest;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.card.form.CardAccountResponse;
import br.com.finc2u.server.features.card.service.CardAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/card-accounts")
@RequiredArgsConstructor
public class CardAccountController {

    private final CardAccountService cardAccountService;

    @PostMapping
    public ResponseEntity<CardAccountResponse> create(@Valid @RequestBody CardAccountRequest cardAccountRequest, @RequestParam UUID userId) {
        CardAccount savedCardAccount = cardAccountService.create(cardAccountRequest.toEntity(), userId);
        return new ResponseEntity<>(CardAccountResponse.from(savedCardAccount), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<CardAccountResponse>> getByUserId(@RequestParam UUID userId) {
        List<CardAccountResponse> cardAccountResponse = cardAccountService.getByUser(userId)
                .stream()
                .map(CardAccountResponse::from)
                .toList();
        return ResponseEntity.ok(cardAccountResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CardAccountResponse> getById(@PathVariable UUID id) {
        CardAccount cardAccount = cardAccountService.getById(id);
        return ResponseEntity.ok(CardAccountResponse.from(cardAccount));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CardAccountResponse> update(@PathVariable UUID id, @Valid @RequestBody CardAccountRequest cardAccountRequest) {
        CardAccount updatedCardAccount = cardAccountService.update(id, cardAccountRequest.toEntity());
        return ResponseEntity.ok(CardAccountResponse.from(updatedCardAccount));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        cardAccountService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
