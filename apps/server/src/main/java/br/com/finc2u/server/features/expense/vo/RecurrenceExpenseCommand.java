package br.com.finc2u.server.features.expense.vo;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/*
 * Os dados da despesa que um modelo recorrente gera num vencimento, sem que a feature expense conheça o modelo: quem gera (o
 * RecurringExpenseMaterializer) monta este VO e o ExpenseService só grava. `recurringExpenseId` liga a despesa ao modelo e
 * `recurrenceMonth` é o mês do modelo em que ela foi gerada (o vencimento pode cair no mês seguinte, com cartão) e garante a
 * idempotência: uma despesa por modelo e mês.
 */
public record RecurrenceExpenseCommand(
    UUID recurringExpenseId,
    YearMonth recurrenceMonth,
    String description,
    BigDecimal value,
    LocalDate dueDate,
    User user,
    Category category,
    CardAccount cardAccount
) {

}
