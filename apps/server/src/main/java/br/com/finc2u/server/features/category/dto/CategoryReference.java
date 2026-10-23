package br.com.finc2u.server.features.category.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;

import java.util.UUID;

/**
 * A categoria de um request, informada por id ou por nome (exatamente um dos dois). Quem a recebe (a despesa e o modelo
 * recorrente) implementa os dois campos e herda as duas validações, com o mesmo caminho e a mesma mensagem de erro.
 */
public interface CategoryReference {

    UUID categoryId();

    String categoryName();

    // A categoria é obrigatória: por id ou por nome.
    @JsonIgnore
    @AssertTrue(message = "Categoria é obrigatória: informe o categoryId ou o categoryName.")
    @SuppressWarnings("unused") // chamado pelo Bean Validation
    default boolean isCategoryInformed() {
        return categoryId() != null || (categoryName() != null && !categoryName().isBlank());
    }

    // ...mas não pelos dois (consistência do próprio request).
    @JsonIgnore
    @AssertTrue(message = "Informe a categoria por id ou por nome, não pelos dois.")
    @SuppressWarnings("unused") // chamado pelo Bean Validation
    default boolean isCategoryReferenceValid() {
        return categoryId() == null || categoryName() == null || categoryName().isBlank();
    }

}
