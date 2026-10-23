package br.com.finc2u.server.features.card.controller;

import br.com.finc2u.server.features.card.dto.CardAccountRequest;
import br.com.finc2u.server.features.card.form.CardAccountResponse;
import br.com.finc2u.server.features.card.service.CardAccountService;
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
@Tag(name = "Cartões", description = "Cartões e contas do usuário. O fechamento e o vencimento do cartão definem em " +
    "qual fatura cai cada compra.")
@RequestMapping("/card-accounts")
@RequiredArgsConstructor
public class CardAccountController {

    private final CardAccountService cardAccountService;

    @PostMapping
    @Operation(
        operationId = "createCardAccount",
        summary = "Cadastra um cartão",
        description = "Cadastra o cartão do usuário. O nome é único por usuário, sem diferenciar maiúsculas de " +
            "minúsculas."
    )
    @ApiResponse(responseCode = "201", description = "Cartão criado.")
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado.")
    @ApiResponse(responseCode = "422", description = "O usuário já tem um cartão com esse nome.")
    public ResponseEntity<CardAccountResponse> create(@Valid @RequestBody CardAccountRequest cardAccountRequest, @RequestParam UUID userId) {
        var savedCardAccount = cardAccountService.create(cardAccountRequest.toEntity(), userId);
        return new ResponseEntity<>(CardAccountResponse.from(savedCardAccount), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(
        operationId = "listCardAccounts",
        summary = "Lista os cartões do usuário",
        description = "Ordenados pelo nome. O parâmetro name faz busca parcial, sem diferenciar maiúsculas de " +
            "minúsculas."
    )
    @ApiResponse(responseCode = "200", description = "Cartões do usuário.")
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado.")
    public ResponseEntity<List<CardAccountResponse>> getByUserId(@RequestParam UUID userId, @RequestParam(required = false) String name) {
        var cardAccountResponse = cardAccountService.getByUser(userId, name)
            .stream()
            .map(CardAccountResponse::from)
            .toList();
        return ResponseEntity.ok(cardAccountResponse);
    }

    @GetMapping("/{id}")
    @Operation(
        operationId = "getCardAccount",
        summary = "Busca um cartão pelo id",
        description = "Devolve o cartão com os dias de fechamento e vencimento e a validade."
    )
    @ApiResponse(responseCode = "200", description = "Cartão encontrado.")
    @ApiResponse(responseCode = "404", description = "Cartão não encontrado.")
    public ResponseEntity<CardAccountResponse> getById(@PathVariable UUID id) {
        var cardAccount = cardAccountService.getById(id);
        return ResponseEntity.ok(CardAccountResponse.from(cardAccount));
    }

    @PutMapping("/{id}")
    @Operation(
        operationId = "updateCardAccount",
        summary = "Altera um cartão",
        description = "Altera nome, fechamento, vencimento e validade. As despesas já lançadas não mudam."
    )
    @ApiResponse(responseCode = "200", description = "Cartão alterado.")
    @ApiResponse(responseCode = "404", description = "Cartão não encontrado.")
    @ApiResponse(responseCode = "422", description = "Outro cartão do usuário já tem esse nome.")
    public ResponseEntity<CardAccountResponse> update(@PathVariable UUID id, @Valid @RequestBody CardAccountRequest cardAccountRequest) {
        var updatedCardAccount = cardAccountService.update(id, cardAccountRequest.toEntity());
        return ResponseEntity.ok(CardAccountResponse.from(updatedCardAccount));
    }

    @DeleteMapping("/{id}")
    @Operation(
        operationId = "deleteCardAccount",
        summary = "Apaga um cartão",
        description = "Apaga o cartão. Com despesas ou despesas recorrentes usando-o, o banco recusa."
    )
    @ApiResponse(responseCode = "204", description = "Cartão removido.")
    @ApiResponse(responseCode = "404", description = "Cartão não encontrado.")
    @ApiResponse(responseCode = "409", description = "Existem despesas ou despesas recorrentes usando o cartão.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        cardAccountService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
