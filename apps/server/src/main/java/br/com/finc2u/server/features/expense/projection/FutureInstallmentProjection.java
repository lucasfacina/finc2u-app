package br.com.finc2u.server.features.expense.projection;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import br.com.finc2u.server.features.user.service.UserService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Projeção das parcelas futuras: uma leitura derivada, fora do CRUD. Como cada parcela é uma linha real, a projeção não calcula
 * parcelas: lê as linhas que já existem e as agrupa por mês.
 */
@Component
public record FutureInstallmentProjection(ExpenseRepository expenseRepository, UserService userService) {

    /**
     * Parcelas futuras do usuário, agrupadas pelo mês de vencimento: linhas {@code PARCELED} e {@code PENDING} com vencimento posterior à
     * data de referência.
     *
     * @param referenceDate "hoje", vinda do {@code Clock} de quem chama (a projeção não lê o relógio)
     * @return as parcelas por mês; sem parcelas futuras, mapa vazio
     * @throws ResourceNotFoundException se o usuário não existir (lançada por {@code UserService#getById})
     */
    public Map<YearMonth, List<Expense>> getProjectionsByUser(UUID userId, LocalDate referenceDate) {
        userService.getById(userId);

        return expenseRepository
            .findByUserIdAndExpenseTypeAndPaymentStatusAndDueDateAfterOrderByDueDate(
                userId, ExpenseType.PARCELED, PaymentStatus.PENDING, referenceDate)
            .stream()
            .collect(Collectors.groupingBy(expense -> YearMonth.from(expense.getDueDate())));
    }

}
