package br.com.finc2u.server.features.summary.vo;

import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.entity.MonthlySummaryCategoryTotal;

import java.util.List;

// Um resumo lido do banco junto com os totais por categoria dele, para o controller montar a resposta sem nova consulta.
public record MonthlySummaryDetail(MonthlySummary summary, List<MonthlySummaryCategoryTotal> categoryTotals) {
}
