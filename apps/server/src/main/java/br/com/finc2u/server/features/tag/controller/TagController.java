package br.com.finc2u.server.features.tag.controller;

import br.com.finc2u.server.features.tag.dto.TagRequest;
import br.com.finc2u.server.features.tag.entity.Tag;
import br.com.finc2u.server.features.tag.form.TagResponse;
import br.com.finc2u.server.features.tag.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @PostMapping
    public ResponseEntity<TagResponse> create(@Valid @RequestBody TagRequest tagRequest) {
        Tag savedTag = tagService.create(tagRequest.toEntity());
        return new ResponseEntity<>(TagResponse.from(savedTag), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<TagResponse>> getAll() {
        List<TagResponse> tagResponse = tagService.getAll()
                .stream()
                .map(TagResponse::from)
                .toList();
        return ResponseEntity.ok(tagResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TagResponse> getById(@PathVariable UUID id) {
        Tag tag = tagService.getById(id);
        return ResponseEntity.ok(TagResponse.from(tag));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TagResponse> update(@PathVariable UUID id, @Valid @RequestBody TagRequest tagRequest) {
        Tag updatedTag = tagService.update(id, tagRequest.toEntity());
        return ResponseEntity.ok(TagResponse.from(updatedTag));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        tagService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
