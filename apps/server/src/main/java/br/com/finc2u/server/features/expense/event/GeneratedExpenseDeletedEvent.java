package br.com.finc2u.server.features.expense.event;

import java.time.YearMonth;
import java.util.UUID;

/*
 * Publicado pelo `ExpenseService` quando o usuário apaga uma despesa que um modelo recorrente havia gerado. Quem cuida da
 * recorrência (a feature recurringexpense) reage e registra aquele mês como "pulado", para a despesa não ser recriada,
 * sem que o `ExpenseService` conheça o modelo.
 */
public record GeneratedExpenseDeletedEvent(
    UUID recurringExpenseId,
    YearMonth recurrenceMonth
) {

}
