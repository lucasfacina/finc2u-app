package br.com.finc2u.server.features.expense.vo;

import br.com.finc2u.server.features.expense.entity.Expense;

import java.time.LocalDate;
import java.util.UUID;

/*
 * Dados de entrada de criação/edição de despesa, já desmembrados do request HTTP: a despesa transitória com os campos
 * editáveis e as referências (cartão por id ou nome, categoria por id ou nome). O service os recebe sem conhecer o DTO,
 * e a assinatura não cresce a cada campo novo.
 */
public record ExpenseCommand(
    Expense expense,
    UUID cardAccountId,
    String cardName,
    UUID categoryId,
    String categoryName,
    LocalDate purchaseDate
) {

    public static ExpenseCommand of(Expense expense) {
        return new ExpenseCommand(expense, null, null, null, null, null);
    }

    public ExpenseCommand withCardAccountId(UUID cardAccountId) {
        return new ExpenseCommand(expense, cardAccountId, cardName, categoryId, categoryName, purchaseDate);
    }

    public ExpenseCommand withCardName(String cardName) {
        return new ExpenseCommand(expense, cardAccountId, cardName, categoryId, categoryName, purchaseDate);
    }

    public ExpenseCommand withCategoryId(UUID categoryId) {
        return new ExpenseCommand(expense, cardAccountId, cardName, categoryId, categoryName, purchaseDate);
    }

    public ExpenseCommand withCategoryName(String categoryName) {
        return new ExpenseCommand(expense, cardAccountId, cardName, categoryId, categoryName, purchaseDate);
    }

    public ExpenseCommand withPurchaseDate(LocalDate purchaseDate) {
        return new ExpenseCommand(expense, cardAccountId, cardName, categoryId, categoryName, purchaseDate);
    }

}
