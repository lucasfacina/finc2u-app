package br.com.finc2u.server.features.category.controller;

import br.com.finc2u.server.features.category.dto.CategoryRequest;
import br.com.finc2u.server.features.category.form.CategoryResponse;
import br.com.finc2u.server.features.category.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest categoryRequest, @RequestParam UUID userId) {
        var savedCategory = categoryService.create(categoryRequest.toEntity(), userId);
        return new ResponseEntity<>(CategoryResponse.from(savedCategory), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getByUserId(@RequestParam UUID userId) {
        var categoryResponse = categoryService.getByUser(userId)
            .stream()
            .map(CategoryResponse::from)
            .toList();
        return ResponseEntity.ok(categoryResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getById(@PathVariable UUID id) {
        var category = categoryService.getById(id);
        return ResponseEntity.ok(CategoryResponse.from(category));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest categoryRequest) {
        var updatedCategory = categoryService.update(id, categoryRequest.toEntity());
        return ResponseEntity.ok(CategoryResponse.from(updatedCategory));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
