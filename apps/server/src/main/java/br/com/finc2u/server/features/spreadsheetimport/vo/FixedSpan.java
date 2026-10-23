package br.com.finc2u.server.features.spreadsheetimport.vo;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

/*
 * Um trecho da assinatura fixa (`FIXOS`): meses seguidos, no mesmo cartão e com o mesmo valor. É o que vira um modelo
 * recorrente. Quando o valor muda, ou falta um mês no meio, começa outro trecho (o modelo recorrente tem um valor só).
 * `entries` guarda a linha de cada mês, com o status de pagamento dela.
 */
public record FixedSpan(String card, String description, BigDecimal value, List<SpreadsheetEntry> entries) {

    public FixedSpan {
        entries = List.copyOf(entries);
    }

    public static List<FixedSpan> from(Collection<SpreadsheetEntry> entries) {
        var series = new LinkedHashMap<String, List<SpreadsheetEntry>>();
        for (var entry : entries) {
            if (entry.description() == null) continue;

            var key = entry.card() + "|" + entry.description().toLowerCase(Locale.ROOT);
            series.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
        }

        var spans = new ArrayList<FixedSpan>();
        for (var serie : series.values()) {
            var sorted = serie.stream().sorted(Comparator.comparing(SpreadsheetEntry::paymentMonth)).toList();
            var current = new ArrayList<SpreadsheetEntry>();

            for (var entry : sorted) {
                var startsNewSpan = !current.isEmpty() && !continues(current.getLast(), entry);
                if (startsNewSpan) {
                    spans.add(of(current));
                    current = new ArrayList<>();
                }
                current.add(entry);
            }

            spans.add(of(current));
        }

        return spans;
    }

    private static boolean continues(SpreadsheetEntry last, SpreadsheetEntry next) {
        return next.paymentMonth().equals(last.paymentMonth().plusMonths(1)) && next.value().compareTo(last.value()) == 0;
    }

    private static FixedSpan of(List<SpreadsheetEntry> entries) {
        var first = entries.getFirst();
        return new FixedSpan(first.card(), first.description(), first.value(), entries);
    }

    public YearMonth firstMonth() {
        return entries.getFirst().paymentMonth();
    }

    public YearMonth lastMonth() {
        return entries.getLast().paymentMonth();
    }

}
