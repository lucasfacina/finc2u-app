package br.com.finc2u.server.features.summary.vo;

import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.entity.MonthlySummaryCategoryTotal;

import java.util.List;

/*
 * O resumo calculado, os totais por categoria gravados com ele e se ele foi criado agora (primeiro cálculo do período) ou
 * já existia e foi recalculado. É o service quem sabe: o controller só traduz o created em 201 ou 200.
 */
public record MonthlySummaryResult(MonthlySummary summary, List<MonthlySummaryCategoryTotal> categoryTotals,
                                   boolean created) {
}
