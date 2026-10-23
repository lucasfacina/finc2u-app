package br.com.finc2u.server.features.spreadsheetimport.vo;

// O que a importação fez; `alreadyThere` conta o que já existia e foi pulado.
public record ImportResult(
    int createdCards,
    int createdRecurringExpenses,
    int createdExpenses,
    int createdExtraIncomes,
    int alreadyThere
) {

    public static ImportResult of(int createdCards, int createdRecurringExpenses, int createdExpenses, int createdExtraIncomes, int alreadyThere) {
        return new ImportResult(createdCards, createdRecurringExpenses, createdExpenses, createdExtraIncomes, alreadyThere);
    }

}
