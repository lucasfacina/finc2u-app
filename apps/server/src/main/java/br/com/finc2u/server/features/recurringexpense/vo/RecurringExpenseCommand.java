package br.com.finc2u.server.features.recurringexpense.vo;

import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;

import java.util.UUID;

/*
 * Dados de entrada de criação/edição do modelo recorrente, já desmembrados do request HTTP: o modelo transitório com os
 * campos editáveis e as referências (categoria obrigatória, por id ou por nome; cartão opcional, por id).
 * O service os recebe sem conhecer o DTO.
 */
public record RecurringExpenseCommand(
    RecurringExpense recurringExpense,
    UUID categoryId,
    String categoryName,
    UUID cardAccountId
) {
}
