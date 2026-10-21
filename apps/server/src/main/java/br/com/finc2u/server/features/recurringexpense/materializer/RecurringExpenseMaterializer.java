package br.com.finc2u.server.features.recurringexpense.materializer;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

/*
 * Transforma o modelo recorrente em despesas reais, uma por mês (FIXED, PENDING). É idempotente: chamar de novo para o
 * mesmo mês não duplica nada (a unique de modelo + vencimento garante no banco). Só gera de "este mês" em diante: os
 * meses passados não são recriados, senão apareceriam como despesas atrasadas que ninguém lançou.
 */
@Component
@RequiredArgsConstructor
public class RecurringExpenseMaterializer {

    private final ExpenseService expenseService;
    private final RecurringExpenseRepository recurringExpenseRepository;
    private final Clock clock;

    // Devolve a despesa criada, ou vazio quando não havia o que criar (fora da vigência, mês passado ou já existente).
    @Transactional
    public Optional<Expense> materialize(RecurringExpense recurringExpense, YearMonth month) {
        if (!recurringExpense.isActiveIn(month) || month.isBefore(YearMonth.now(clock))) {
            return Optional.empty();
        }

        return expenseService.createFromRecurrence(recurringExpense, recurringExpense.dueDateIn(month));
    }

    // Mês corrente e seguinte: a assinatura já aparece no mês que vem sem esperar o job.
    @Transactional
    public void materializeUpcoming(RecurringExpense recurringExpense) {
        var currentMonth = YearMonth.now(clock);
        materialize(recurringExpense, currentMonth);
        materialize(recurringExpense, currentMonth.plusMonths(1));
    }

    /*
     * Garante, para todos os modelos do usuário, as despesas que o resumo do mês enxerga. A do mês anterior entra junto:
     * com cartão, a compra do mês anterior pode vencer neste mês (a fatura fecha antes do vencimento).
     */
    @Transactional
    public void materializeForUser(UUID userId, YearMonth month) {
        recurringExpenseRepository.findByUserIdOrderByDescriptionAsc(userId).forEach(recurringExpense -> {
            materialize(recurringExpense, month.minusMonths(1));
            materialize(recurringExpense, month);
        });
    }

}
