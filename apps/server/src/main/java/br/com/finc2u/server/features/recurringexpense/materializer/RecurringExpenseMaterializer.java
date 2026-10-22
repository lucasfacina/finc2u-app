package br.com.finc2u.server.features.recurringexpense.materializer;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseRepository;
import br.com.finc2u.server.shared.event.PeriodDataChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
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
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    // Devolve a despesa criada, ou vazio quando não havia o que criar (fora da vigência, mês passado ou já existente).
    @Transactional
    public Optional<Expense> materialize(RecurringExpense recurringExpense, YearMonth month) {
        if (!recurringExpense.isActiveIn(month) || month.isBefore(YearMonth.now(clock))) {
            return Optional.empty();
        }

        return expenseService.createFromRecurrence(recurringExpense, recurringExpense.dueDateIn(month));
    }

    /*
     * Mês corrente e seguinte: a assinatura já aparece no mês que vem sem esperar o job. É o ponto que o cadastro do modelo e o
     * job usam, e o único que avisa o resumo mensal (PeriodDataChangedEvent): as despesas criadas mudam os resumos que já
     * existem a partir do vencimento mais antigo delas. Rodar de novo, sem criar nada, não avisa. O materialize e o
     * materializeForUser não avisam: o segundo roda dentro do cálculo do resumo (avisar de lá seria recursão).
     */
    @Transactional
    public void materializeUpcoming(RecurringExpense recurringExpense) {
        var currentMonth = YearMonth.now(clock);
        var created = new ArrayList<Expense>();
        materialize(recurringExpense, currentMonth).ifPresent(created::add);
        materialize(recurringExpense, currentMonth.plusMonths(1)).ifPresent(created::add);

        if (!created.isEmpty()) {
            eventPublisher.publishEvent(PeriodDataChangedEvent.earliestOf(
                recurringExpense.getUser().getId(), created.stream().map(Expense::getDueDate).toList()));
        }
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
