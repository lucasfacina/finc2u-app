package br.com.finc2u.server.features.expense.projection;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public record FutureInstallmentProjection(ExpenseRepository expenseRepository) {

    /**
     * Monta as parcelas virtuais (não persistidas) restantes de uma despesa parcelada — uma por mês
     * subsequente ao vencimento atual; já na última parcela, retorna lista vazia.
     */
    private static List<Expense> remainingInstallmentsOf(Expense source) {
        if (source.getTotalInstallment() == null) {
            return List.of();
        }

        int current = source.getCurrentInstallment() != null
                ? source.getCurrentInstallment()
                : 1;
        int total = source.getTotalInstallment();
        int remaining = total - current;

        List<Expense> projections = new ArrayList<>();
        for (int i = 1; i <= remaining; i++) {
            projections.add(Expense.builder()
                    .description(source.getDescription() + " (" + (current + i) + "/" + total + ")")
                    .value(source.getValue())
                    .dueDate(source.getDueDate().plusMonths(i))
                    .expenseType(source.getExpenseType())
                    .paymentStatus(PaymentStatus.PENDING)
                    .currentInstallment(current + i)
                    .totalInstallment(total)
                    .cardAccount(source.getCardAccount())
                    .build());
        }
        return projections;
    }

    public Map<YearMonth, List<Expense>> getProjectionsByUser(UUID userId) {
        return expenseRepository.findByExpenseTypeAndUserId(ExpenseType.PARCELED, userId)
                .stream()
                .filter(expense -> expense.getPaymentStatus() == PaymentStatus.PENDING)
                .flatMap(expense -> remainingInstallmentsOf(expense).stream())
                .collect(Collectors.groupingBy(expense -> YearMonth.from(expense.getDueDate())));
    }

}
