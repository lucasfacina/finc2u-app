package br.com.finc2u.server.features.expense.filter;

import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

/*
 * Os critérios de filtragem das despesas, como valor puro: o service e a ExpenseSpecification o recebem já validado. A
 * validação do que chega pelo HTTP (mês sem ano, faixa de preço) é do ExpenseFilterRequest. Critério nulo = sem restrição.
 */
public record ExpenseFilter(
    Integer month,
    Integer year,
    PaymentStatus status,
    ExpenseType type,
    UUID cardAccountId,
    UUID categoryId,
    BigDecimal minPrice,
    BigDecimal maxPrice,

    // true: só as pendentes já vencidas (atraso derivado); false ou ausente: sem restrição.
    Boolean overdue
) {

}
