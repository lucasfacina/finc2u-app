package br.com.finc2u.server.features.recurringexpense.mapper;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.card.service.CardAccountService;
import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.vo.RecurringExpenseCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Monta o modelo recorrente a partir dos dados do request: resolve a categoria e o cartão delegando aos services de cada feature
 * ({@code CategoryService}, {@code CardAccountService}) e copia os campos editáveis do modelo transitório (montado no controller)
 * para o modelo gerenciado. Mantém o {@code RecurringExpenseService} focado no fluxo CRUD, no mesmo papel do {@code ExpenseMapper}.
 */
@Component
@RequiredArgsConstructor
public class RecurringExpenseMapper {

    private final CategoryService categoryService;
    private final CardAccountService cardAccountService;

    /**
     * Resolve e vincula a categoria (obrigatória, por id ou por nome; o id vence o nome) e o cartão (opcional, por id) do dono do
     * modelo, que já deve estar definido nele. Sem cartão no comando, desvincula o que houver (despesa avulsa). Por id, a categoria
     * e o cartão de outro usuário são tratados como inexistentes; por nome, a categoria do usuário é criada na hora se ainda não
     * existir.
     *
     * @throws ResourceNotFoundException se o id da categoria ou do cartão não existir ou for de outro usuário
     *                                   (lançada por {@code CategoryService#getByIdAndUser} e {@code CardAccountService#getByIdAndUser})
     */
    public void applyReferences(RecurringExpense recurringExpense, RecurringExpenseCommand command) {
        var ownerId = recurringExpense.getUser().getId();

        recurringExpense.setCategory(command.categoryId() != null
            ? categoryService.getByIdAndUser(command.categoryId(), ownerId)
            : categoryService.getOrCreateByName(ownerId, command.categoryName()));
        recurringExpense.setCardAccount(command.cardAccountId() != null
            ? cardAccountService.getByIdAndUser(command.cardAccountId(), ownerId)
            : null);
    }

    /**
     * Copia do modelo transitório para o gerenciado só os campos que o usuário edita: descrição, valor, dia do mês, mês de início e
     * mês de término. Não mexe no dono, na categoria, no cartão nem no id (as referências têm a própria operação).
     */
    public void applyEditableFields(RecurringExpense target, RecurringExpense source) {
        target.setDescription(source.getDescription());
        target.setValue(source.getValue());
        target.setDayOfMonth(source.getDayOfMonth());
        target.setStartMonth(source.getStartMonth());
        target.setEndMonth(source.getEndMonth());
    }

}
