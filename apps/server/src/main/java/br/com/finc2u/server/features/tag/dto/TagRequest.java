package br.com.finc2u.server.features.tag.dto;

import br.com.finc2u.server.features.tag.entity.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 60, message = "Nome deve conter no máximo 60 caracteres")
        String name,

        Long colorCode
) {

    public Tag toEntity() {
        return Tag.builder()
                .name(this.name())
                .colorCode(this.colorCode())
                .build();
    }

}
