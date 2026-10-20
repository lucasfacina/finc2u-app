package br.com.finc2u.server.features.expense.mapper;

import br.com.finc2u.server.features.card.service.CardAccountService;
import br.com.finc2u.server.features.expense.entity.Expense;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/*
 * Monta a entidade de despesa a partir dos dados do request: resolve as associações (FK) por id,
 * delegando às services de cada feature (`CardAccountService`) e copia os campos
 * editáveis da despesa transitória (montada no controller) para a entidade gerenciada. Mantém o
 * `ExpenseService` focado no fluxo CRUD e a entidade livre do trabalho de mapeamento request→entidade.
 */
@Component
@RequiredArgsConstructor
public class ExpenseMapper {

    private final CardAccountService cardAccountService;

    // Resolve e vincula o cartão; id nulo desvincula (despesa avulsa).
    public void applyCardAccount(Expense expense, UUID cardAccountId) {
        expense.setCardAccount(cardAccountId == null
            ? null
            : cardAccountService.getById(cardAccountId)
        );
    }

    // Copia os campos editáveis da despesa montada a partir do request para a entidade gerenciada.
    public void applyEditableFields(Expense target, Expense source) {
        target.setDescription(source.getDescription());
        target.setValue(source.getValue());
        target.setDueDate(source.getDueDate());
        target.setExpenseType(source.getExpenseType());
        target.setPaymentStatus(source.getPaymentStatus());
        target.setCurrentInstallment(source.getCurrentInstallment());
        target.setTotalInstallment(source.getTotalInstallment());
    }

}
