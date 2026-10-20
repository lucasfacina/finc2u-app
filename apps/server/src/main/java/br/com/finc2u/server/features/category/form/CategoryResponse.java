package br.com.finc2u.server.features.category.form;

import br.com.finc2u.server.features.category.entity.Category;

import java.util.UUID;

public record CategoryResponse(
    UUID id,
    String name
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
            category.getId(),
            category.getName()
        );
    }

}
