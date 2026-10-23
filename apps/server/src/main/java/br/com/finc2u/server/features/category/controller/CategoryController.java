package br.com.finc2u.server.features.category.controller;

import br.com.finc2u.server.features.category.dto.CategoryRequest;
import br.com.finc2u.server.features.category.form.CategoryResponse;
import br.com.finc2u.server.features.category.service.CategoryService;
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
@Tag(name = "Categorias", description = "Categorias de despesa do usuário. As categorias padrão nascem no cadastro do " +
    "usuário.")
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @Operation(
        operationId = "createCategory",
        summary = "Cadastra uma categoria",
        description = "Cadastra a categoria do usuário. O nome é único por usuário, sem diferenciar maiúsculas de " +
            "minúsculas."
    )
    @ApiResponse(responseCode = "201", description = "Categoria criada.")
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado.")
    @ApiResponse(responseCode = "422", description = "O usuário já tem uma categoria com esse nome.")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest categoryRequest, @RequestParam UUID userId) {
        var savedCategory = categoryService.create(categoryRequest.toEntity(), userId);
        return new ResponseEntity<>(CategoryResponse.from(savedCategory), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(
        operationId = "listCategories",
        summary = "Lista as categorias do usuário",
        description = "Ordenadas pelo nome."
    )
    @ApiResponse(responseCode = "200", description = "Categorias do usuário.")
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado.")
    public ResponseEntity<List<CategoryResponse>> getByUserId(@RequestParam UUID userId) {
        var categoryResponse = categoryService.getByUser(userId)
            .stream()
            .map(CategoryResponse::from)
            .toList();
        return ResponseEntity.ok(categoryResponse);
    }

    @GetMapping("/{id}")
    @Operation(
        operationId = "getCategory",
        summary = "Busca uma categoria pelo id",
        description = "Devolve a categoria."
    )
    @ApiResponse(responseCode = "200", description = "Categoria encontrada.")
    @ApiResponse(responseCode = "404", description = "Categoria não encontrada.")
    public ResponseEntity<CategoryResponse> getById(@PathVariable UUID id) {
        var category = categoryService.getById(id);
        return ResponseEntity.ok(CategoryResponse.from(category));
    }

    @PutMapping("/{id}")
    @Operation(
        operationId = "updateCategory",
        summary = "Renomeia uma categoria",
        description = "Altera o nome da categoria."
    )
    @ApiResponse(responseCode = "200", description = "Categoria alterada.")
    @ApiResponse(responseCode = "404", description = "Categoria não encontrada.")
    @ApiResponse(responseCode = "422", description = "Outra categoria do usuário já tem esse nome.")
    public ResponseEntity<CategoryResponse> update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest categoryRequest) {
        var updatedCategory = categoryService.update(id, categoryRequest.toEntity());
        return ResponseEntity.ok(CategoryResponse.from(updatedCategory));
    }

    @DeleteMapping("/{id}")
    @Operation(
        operationId = "deleteCategory",
        summary = "Apaga uma categoria",
        description = "Apaga a categoria. Com despesas ou despesas recorrentes usando-a, o banco recusa."
    )
    @ApiResponse(responseCode = "204", description = "Categoria removida.")
    @ApiResponse(responseCode = "404", description = "Categoria não encontrada.")
    @ApiResponse(responseCode = "409", description = "Existem despesas ou despesas recorrentes usando a categoria.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
