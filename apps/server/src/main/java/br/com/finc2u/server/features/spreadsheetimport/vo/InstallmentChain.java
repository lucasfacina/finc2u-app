package br.com.finc2u.server.features.spreadsheetimport.vo;

import java.time.YearMonth;
import java.util.*;

/*
 * As parcelas da planilha que são a mesma compra, em ordem de mês. A planilha não liga uma parcela à outra, mas a compra
 * deixa uma marca: o mês da parcela 1 (mês de pagamento - (n - 1)) é o mesmo em todas as suas parcelas. Então agrupa por
 * cartão, descrição, total e esse mês de origem; dentro do grupo, a i-ésima menor parcela de cada mês é a i-ésima compra
 * (os valores variam em centavos de um mês para o outro, e duas compras do mesmo mês e total têm valores bem diferentes). Se a
 * ligação errar, só a ligação errada se perde: cada parcela lida mantém o valor e o mês que a planilha deu.
 */
public record InstallmentChain(String card, String description, int total, List<SpreadsheetEntry> entries) {

    public InstallmentChain {
        entries = List.copyOf(entries);
    }

    public static List<InstallmentChain> from(Collection<SpreadsheetEntry> entries) {
        record Bucket(String card, String description, int total, YearMonth origin) {
        }

        var buckets = new LinkedHashMap<Bucket, Map<YearMonth, List<SpreadsheetEntry>>>();
        for (var entry : entries) {
            if (entry.installment() == null) continue;

            var origin = entry.paymentMonth().minusMonths(entry.installment() - 1L);
            var bucket = new Bucket(entry.card(), entry.description(), entry.totalInstallments(), origin);
            buckets.computeIfAbsent(bucket, key -> new TreeMap<>())
                .computeIfAbsent(entry.paymentMonth(), month -> new ArrayList<>())
                .add(entry);
        }

        var chains = new ArrayList<InstallmentChain>();
        buckets.forEach((bucket, byMonth) -> {
            var purchases = byMonth.values().stream().mapToInt(List::size).max().orElse(0);

            for (var purchase = 0; purchase < purchases; purchase++) {
                var rank = purchase;
                var chain = new ArrayList<SpreadsheetEntry>();

                byMonth.values().forEach(monthEntries -> {
                    var sorted = monthEntries.stream().sorted(Comparator.comparing(SpreadsheetEntry::value)).toList();
                    if (rank < sorted.size()) chain.add(sorted.get(rank));
                });

                chains.add(new InstallmentChain(bucket.card(), bucket.description(), bucket.total(), chain));
            }
        });

        return chains;
    }

    public SpreadsheetEntry first() {
        return entries.getFirst();
    }

}
