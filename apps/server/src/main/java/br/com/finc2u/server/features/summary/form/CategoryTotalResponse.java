package br.com.finc2u.server.features.summary.form;

import br.com.finc2u.server.features.summary.entity.MonthlySummaryCategoryTotal;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Total gasto em uma categoria no mês.")
public record CategoryTotalResponse(

    @Schema(
        description = "Id da categoria.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    UUID categoryId,

    @Schema(
        description = "Nome da categoria.",
        example = "Alimentação"
    )
    String categoryName,

    @Schema(
        description = "Total gasto na categoria no mês (pago + pendente).",
        example = "850.00"
    )
    BigDecimal total

) {

    public static CategoryTotalResponse from(MonthlySummaryCategoryTotal categoryTotal) {
        return new CategoryTotalResponse(
            categoryTotal.getCategory().getId(),
            categoryTotal.getCategory().getName(),
            categoryTotal.getTotal()
        );
    }

}
