package br.com.finc2u.server.features.spreadsheetimport.vo;

import java.text.Normalizer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/*
 * A regra "trecho da descrição → categoria" (a primeira que casar vence, sem diferenciar maiúsculas nem acentos; o
 * que não casar vai para a categoria padrão, "Outros") e o dia do vencimento das contas e dos extras (opcional: sem
 * ele, vale o último dia do mês de pagamento). Os itens de cartão seguem o vencimento do próprio cartão.
 */
public record ImportOptions(Map<String, String> categoryByKeyword, String defaultCategory, Integer dueDay) {

    private static final String DEFAULT_CATEGORY = "Outros";

    public ImportOptions {
        var invalidDay = dueDay != null && (dueDay < 1 || dueDay > 31);
        if (invalidDay) throw new IllegalArgumentException("O dia do vencimento deve estar entre 1 e 31.");

        categoryByKeyword = categoryByKeyword == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(categoryByKeyword));
        defaultCategory = defaultCategory == null || defaultCategory.isBlank()
            ? DEFAULT_CATEGORY
            : defaultCategory.trim();
    }

    public static ImportOptions of(Map<String, String> categoryByKeyword, String defaultCategory, Integer dueDay) {
        return new ImportOptions(categoryByKeyword, defaultCategory, dueDay);
    }

    public static ImportOptions defaults() {
        return of(null, null, null);
    }

    public String categoryFor(String description) {
        if (description == null) return defaultCategory;

        var text = fold(description);
        for (var rule : categoryByKeyword.entrySet()) {
            var matches = text.contains(fold(rule.getKey()));
            if (matches) return rule.getValue();
        }

        return defaultCategory;
    }

    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

}
