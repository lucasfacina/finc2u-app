package br.com.finc2u.server.features.user.controller;

import br.com.finc2u.server.features.user.dto.UserRequest;
import br.com.finc2u.server.features.user.dto.UserUpdateRequest;
import br.com.finc2u.server.features.user.form.UserResponse;
import br.com.finc2u.server.features.user.projection.UserProfileProjection;
import br.com.finc2u.server.features.user.service.UserService;
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
@Tag(name = "Usuários", description = "Cadastro e consulta dos usuários. O salário não é do usuário: vem do histórico " +
    "de salários.")
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserProfileProjection userProfileProjection;

    @PostMapping
    @Operation(
        operationId = "createUser",
        summary = "Cadastra um usuário",
        description = "Cria o usuário com o primeiro salário (vigente a partir de effectiveFrom; padrão: dia 1 do mês " +
            "de hoje) e as categorias padrão. O e-mail é único."
    )
    @ApiResponse(responseCode = "201", description = "Usuário criado.")
    @ApiResponse(responseCode = "422", description = "E-mail já cadastrado.")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest userRequest) {
        var savedUser = userService.create(userRequest.toEntity(), userRequest.baseSalary(), userRequest.effectiveFrom());
        return new ResponseEntity<>(userProfileProjection.toProfile(savedUser), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(
        operationId = "listUsers",
        summary = "Lista os usuários",
        description = "Todos os usuários cadastrados, com o salário vigente hoje."
    )
    public ResponseEntity<List<UserResponse>> getAll() {
        return ResponseEntity.ok(userProfileProjection.getProfiles());
    }

    @GetMapping("/{id}")
    @Operation(
        operationId = "getUser",
        summary = "Busca um usuário pelo id",
        description = "Devolve o usuário com o salário vigente hoje."
    )
    @ApiResponse(responseCode = "200", description = "Usuário encontrado.")
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado.")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(userProfileProjection.getProfile(id));
    }

    @PutMapping("/{id}")
    @Operation(
        operationId = "updateUser",
        summary = "Altera um usuário",
        description = "Altera nome, e-mail e saldo de poupança. O salário não muda aqui: use o histórico de salários."
    )
    @ApiResponse(responseCode = "200", description = "Usuário alterado.")
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado.")
    @ApiResponse(responseCode = "422", description = "O e-mail já pertence a outro usuário.")
    public ResponseEntity<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UserUpdateRequest userUpdateRequest) {
        var updatedUser = userService.update(id, userUpdateRequest.toEntity());
        return ResponseEntity.ok(userProfileProjection.toProfile(updatedUser));
    }

    @DeleteMapping("/{id}")
    @Operation(
        operationId = "deleteUser",
        summary = "Apaga um usuário",
        description = "Apaga o usuário. Se houver dados vinculados (as categorias padrão e o salário nascem no " +
            "cadastro), o banco recusa."
    )
    @ApiResponse(responseCode = "204", description = "Usuário removido.")
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado.")
    @ApiResponse(responseCode = "409", description = "Existem registros vinculados ao usuário.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
