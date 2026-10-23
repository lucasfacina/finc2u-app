package br.com.finc2u.server.features.category.form;

import br.com.finc2u.server.features.category.entity.Category;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Categoria de despesa.")
public record CategoryResponse(

    @Schema(
        description = "Id da categoria.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    UUID id,

    @Schema(
        description = "Nome da categoria.",
        example = "Alimentação"
    )
    String name

) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
            category.getId(),
            category.getName()
        );
    }

}
