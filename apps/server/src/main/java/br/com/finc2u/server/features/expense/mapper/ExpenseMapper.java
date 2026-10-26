package br.com.finc2u.server.features.expense.mapper;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.card.service.CardAccountService;
import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.expense.entity.Expense;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Monta a entidade de despesa a partir dos dados do request: resolve as associações (FK) delegando aos services de cada feature
 * ({@code CardAccountService}, {@code CategoryService}) e copia os campos editáveis da despesa transitória (montada no controller)
 * para a entidade gerenciada. Mantém o {@code ExpenseService} focado no fluxo CRUD e a entidade livre do trabalho de mapeamento
 * request→entidade.
 */
@Component
@RequiredArgsConstructor
public class ExpenseMapper {

    private final CardAccountService cardAccountService;
    private final CategoryService categoryService;

    /**
     * Resolve e vincula o cartão do dono da despesa, por id ou por nome; sem nenhum dos dois, desvincula (despesa avulsa). O id vence
     * o nome.
     *
     * @throws ResourceNotFoundException se o cartão não existir ou for de outro usuário (tratado como inexistente)
     *                                   (lançada por {@code CardAccountService#getByIdAndUser} e {@code CardAccountService#getByUserAndName})
     */
    public void applyCardAccount(Expense expense, UUID cardAccountId, String cardName) {
        if (cardAccountId != null) {
            expense.setCardAccount(cardAccountService.getByIdAndUser(cardAccountId, expense.getUser().getId()));
        } else if (cardName != null && !cardName.isBlank()) {
            expense.setCardAccount(cardAccountService.getByUserAndName(expense.getUser().getId(), cardName));
        } else {
            expense.setCardAccount(null);
        }
    }

    /**
     * Resolve e vincula a categoria do dono da despesa (obrigatória), por id ou por nome. O id vence o nome. Por id, a categoria de
     * outro usuário é tratada como inexistente; por nome, a categoria do usuário é criada na hora se ainda não existir.
     *
     * @throws ResourceNotFoundException se o id não existir ou for de outro usuário
     *                                   (lançada por {@code CategoryService#getByIdAndUser})
     */
    public void applyCategory(Expense expense, UUID categoryId, String categoryName) {
        var ownerId = expense.getUser().getId();
        expense.setCategory(categoryId != null
            ? categoryService.getByIdAndUser(categoryId, ownerId)
            : categoryService.getOrCreateByName(ownerId, categoryName));
    }

    /**
     * Copia da despesa transitória para a gerenciada só os campos que o usuário edita: descrição, valor, vencimento, status, data do
     * pagamento e, numa despesa fora de um grupo de parcelas, também o tipo e as parcelas. Numa parcela (com grupo) o tipo e a
     * numeração são a identidade dela e não são copiados. Não copia o dono, o cartão e a categoria (têm as próprias operações acima)
     * nem o grupo da compra parcelada e o vínculo com o modelo recorrente, que o usuário não altera.
     */
    public void applyEditableFields(Expense target, Expense source) {
        target.setDescription(source.getDescription());
        target.setValue(source.getValue());
        target.setDueDate(source.getDueDate());
        target.setPaymentStatus(source.getPaymentStatus());
        target.setPaidAt(source.getPaidAt());

        if (target.getInstallmentGroupId() != null) return;

        target.setExpenseType(source.getExpenseType());
        target.setCurrentInstallment(source.getCurrentInstallment());
        target.setTotalInstallment(source.getTotalInstallment());
    }

}
