package br.com.finc2u.server.features.summary.form;

import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.entity.MonthlySummaryCategoryTotal;
import br.com.finc2u.server.features.summary.vo.MonthlySummaryDetail;
import br.com.finc2u.server.features.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MonthlySummaryResponseTest {

    // O cliente sabe a que "hoje" o totalOverdue se refere; resumo antigo (sem a data) sai com nulo, sem erro.
    @Test
    void from_shouldExposeTheReferenceDateTheSummaryWasCalculatedOn_orNullWhenItHasNone() {
        var user = User.builder().name("Lucas").build();
        user.setId(UUID.randomUUID());
        var summary = MonthlySummary.forPeriod(user, 6, 2026);

        assertNull(MonthlySummaryResponse.from(summary, List.of()).referenceDate());

        summary.setReferenceDate(LocalDate.of(2026, 6, 15));

        assertEquals(LocalDate.of(2026, 6, 15), MonthlySummaryResponse.from(summary, List.of()).referenceDate());
    }

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
