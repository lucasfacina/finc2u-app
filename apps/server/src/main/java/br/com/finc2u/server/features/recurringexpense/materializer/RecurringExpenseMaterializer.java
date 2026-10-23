package br.com.finc2u.server.features.recurringexpense.materializer;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.expense.vo.RecurrenceExpenseCommand;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseRepository;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseSkipRepository;
import br.com.finc2u.server.shared.event.PeriodDataChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

/*
 * Transforma o modelo recorrente em despesas reais, uma por mês (FIXED, PENDING). É idempotente: chamar de novo para o
 * mesmo mês não duplica nada (a unique de modelo + vencimento garante no banco). Só gera de "este mês" em diante: os
 * meses passados não são recriados, senão apareceriam como despesas atrasadas que ninguém lançou. Um vencimento que o usuário
 * apagou de propósito (mês pulado) também não volta, nem no resumo nem pelo job. E o que já venceu não nasce atrasado: a
 * assinatura vale a partir do próximo vencimento. Duas gerações simultâneas do mesmo modelo são serializadas por um lock na
 * linha dele (a segunda espera e então vê a despesa já criada).
 */
@Component
@RequiredArgsConstructor
public class RecurringExpenseMaterializer {

    private final ExpenseService expenseService;
    private final RecurringExpenseRepository recurringExpenseRepository;
    private final RecurringExpenseSkipRepository recurringExpenseSkipRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    // Devolve a despesa criada, ou vazio quando não havia o que criar (fora da vigência, mês passado, vencimento já passado, mês pulado
    // ou já existente).
    @Transactional
    public Optional<Expense> materialize(RecurringExpense recurringExpense, YearMonth month) {
        if (!recurringExpense.isActiveIn(month) || month.isBefore(YearMonth.now(clock))) return Optional.empty();

        var dueDate = recurringExpense.dueDateIn(month);
        if (dueDate.isBefore(LocalDate.now(clock))) return Optional.empty();

        // Serializa a geração do mesmo modelo: duas transações (o resumo e o job, por exemplo) esperam aqui uma pela outra, e a segunda
        // já enxerga o que a primeira criou, em vez de estourar a unique de modelo + vencimento no commit.
        recurringExpenseRepository.findByIdForUpdate(recurringExpense.getId());

        if (recurringExpenseSkipRepository.existsByRecurringExpenseIdAndDueDate(recurringExpense.getId(), dueDate))
            return Optional.empty();

        return expenseService.createFromRecurrence(commandFor(recurringExpense, dueDate));
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

    // A feature expense não conhece o modelo: o VO leva só o que a despesa gerada precisa.
    private RecurrenceExpenseCommand commandFor(RecurringExpense recurringExpense, LocalDate dueDate) {
        return new RecurrenceExpenseCommand(
            recurringExpense.getId(),
            recurringExpense.getDescription(),
            recurringExpense.getValue(),
            dueDate,
            recurringExpense.getUser(),
            recurringExpense.getCategory(),
            recurringExpense.getCardAccount()
        );
    }

}
