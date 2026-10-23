package br.com.finc2u.server.features.summary.form;

import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.entity.MonthlySummaryCategoryTotal;
import br.com.finc2u.server.features.summary.vo.MonthlySummaryDetail;
import br.com.finc2u.server.features.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MonthlySummaryResponseTest {

    @Test
    void from_shouldBuildTheSameResponseFromTheDetailAsFromItsParts() {
        var user = User.builder().name("Lucas").build();
        user.setId(UUID.randomUUID());
        var summary = MonthlySummary.forPeriod(user, 6, 2026);
        summary.setTotalExpenses(BigDecimal.valueOf(350));
        var totals = List.of(MonthlySummaryCategoryTotal.of(summary, Category.of("Alimentação"), BigDecimal.valueOf(350)));

        var fromDetail = MonthlySummaryResponse.from(new MonthlySummaryDetail(summary, totals));

        assertEquals(MonthlySummaryResponse.from(summary, totals), fromDetail);
        assertEquals(user.getId(), fromDetail.userId());
        assertEquals(List.of("Alimentação"), fromDetail.categoryTotals().stream().map(CategoryTotalResponse::categoryName).toList());
    }

}
