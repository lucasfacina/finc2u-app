package br.com.finc2u.server.features.expense.vo;

import br.com.finc2u.server.features.expense.entity.Expense;

import java.time.LocalDate;
import java.util.UUID;

/*
 * Dados de entrada de criação/edição de despesa, já desmembrados do request HTTP: a despesa transitória com os campos
 * editáveis e as referências (cartão por id ou nome, categoria por id). O service os recebe sem conhecer o DTO, e a assinatura não
 * cresce a cada campo novo.
 */
public record ExpenseCommand(
    Expense expense,
    UUID cardAccountId,
    String cardName,
    UUID categoryId,
    LocalDate purchaseDate
) {
}
