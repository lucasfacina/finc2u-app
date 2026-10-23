package br.com.finc2u.server.features.category.dto;

import br.com.finc2u.server.features.category.entity.Category;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para cadastrar ou renomear uma categoria.")
public record CategoryRequest(

    @NotBlank(message = "Nome é obrigatório!")
    @Size(max = 60, message = "Nome deve conter no máximo 60 caracteres.")
    @Schema(
        description = "Nome da categoria (até 60 caracteres). Único por usuário, sem diferenciar maiúsculas de minúsculas.",
        example = "Alimentação"
    )
    String name

) {

    public Category toEntity() {
        return Category.of(this.name());
    }

}
