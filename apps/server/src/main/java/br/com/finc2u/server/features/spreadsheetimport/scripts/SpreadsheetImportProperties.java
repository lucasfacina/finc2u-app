package br.com.finc2u.server.features.spreadsheetimport.scripts;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;

import java.nio.file.Path;
import java.time.YearMonth;

/*
 * As propriedades locais do importador (`app.import.*`). Só `planilha` e `email` são obrigatórias; sem `planilha` o importador
 * nem existe. `primeiro-mes` é o primeiro bloco lido (padrão abr/2025, quando a planilha passa a ser itemizada),
 * `dia-vencimento` e `categoria-padrao` entram nas `ImportOptions`, e `regras-categoria` é o arquivo local "trecho = Categoria".
 */
@ConfigurationProperties(prefix = "app.import")
public record SpreadsheetImportProperties(
    @Name("planilha") Path spreadsheet,
    String email,
    @Name("primeiro-mes") YearMonth firstMonth,
    @Name("dia-vencimento") Integer dueDay,
    @Name("regras-categoria") Path categoryRules,
    @Name("categoria-padrao") String defaultCategory
) {

    private static final YearMonth DEFAULT_FIRST_MONTH = YearMonth.of(2025, 4);

    public SpreadsheetImportProperties {
        firstMonth = firstMonth == null
            ? DEFAULT_FIRST_MONTH
            : firstMonth;
    }

}
