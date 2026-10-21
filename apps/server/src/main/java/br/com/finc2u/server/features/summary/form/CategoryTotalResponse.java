package br.com.finc2u.server.features.summary.form;

import br.com.finc2u.server.features.summary.entity.MonthlySummaryCategoryTotal;

import java.math.BigDecimal;
import java.util.UUID;

public record CategoryTotalResponse(
    UUID categoryId,
    String categoryName,
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
