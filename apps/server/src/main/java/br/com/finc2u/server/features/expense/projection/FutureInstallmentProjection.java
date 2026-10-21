package br.com.finc2u.server.features.expense.projection;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public record FutureInstallmentProjection(ExpenseRepository expenseRepository) {

    /*
     * Parcelas futuras do usuário, agrupadas pelo mês de vencimento. Desde que a compra parcelada é materializada (uma
     * linha por parcela), a projeção só lê as linhas reais: PARCELED e PENDING com vencimento posterior à data de
     * referência (hoje, vinda do Clock do chamador). Nada é montado em memória.
     */
    public Map<YearMonth, List<Expense>> getProjectionsByUser(UUID userId, LocalDate referenceDate) {
        return expenseRepository
            .findByUserIdAndExpenseTypeAndPaymentStatusAndDueDateAfterOrderByDueDate(
                userId, ExpenseType.PARCELED, PaymentStatus.PENDING, referenceDate)
            .stream()
            .collect(Collectors.groupingBy(expense -> YearMonth.from(expense.getDueDate())));
    }

}
