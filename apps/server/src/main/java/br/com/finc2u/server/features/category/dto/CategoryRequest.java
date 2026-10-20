package br.com.finc2u.server.features.category.dto;

import br.com.finc2u.server.features.category.entity.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
    @NotBlank(message = "Nome é obrigatório!")
    @Size(max = 60, message = "Nome deve conter no máximo 60 caracteres.")
    String name
) {

    public Category toEntity() {
        return Category.of(this.name());
    }

}
