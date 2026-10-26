package br.com.finc2u.server.features.recurringexpense.materializer;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.service.ExpenseService;
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

/**
 * Transforma o modelo recorrente em despesas reais, uma por mês ({@code FIXED}, {@code PENDING}).
 * <ul>
 *   <li><b>Idempotente:</b> chamar de novo para o mesmo mês não duplica nada (a unique de modelo + mês garante no banco).</li>
 *   <li><b>Só de "este mês" em diante:</b> os meses passados não são recriados, senão apareceriam como despesas atrasadas que
 *       ninguém lançou. E o que já venceu não nasce atrasado: a assinatura vale a partir do próximo vencimento.</li>
 *   <li><b>Mês pulado não volta:</b> um vencimento que o usuário apagou de propósito não é recriado, nem no resumo nem pelo job.</li>
 *   <li><b>Seguro em paralelo:</b> duas gerações simultâneas do mesmo modelo são serializadas por um lock na linha dele (a segunda
 *       espera e então vê a despesa já criada).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RecurringExpenseMaterializer {

    private final ExpenseService expenseService;
    private final RecurringExpenseRepository recurringExpenseRepository;
    private final RecurringExpenseSkipRepository recurringExpenseSkipRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Gera a despesa do modelo no mês, se couber. Não avisa o resumo mensal (quem chama é que decide).
     *
     * @return a despesa criada, ou vazio quando não havia o que criar: fora da vigência, mês passado, vencimento já passado, mês
     * pulado ou já existente
     */
    @Transactional
    public Optional<Expense> materialize(RecurringExpense recurringExpense, YearMonth month) {
        if (!recurringExpense.isActiveIn(month) || month.isBefore(YearMonth.now(clock))) return Optional.empty();

        var dueDate = recurringExpense.dueDateIn(month);
        if (dueDate.isBefore(LocalDate.now(clock))) return Optional.empty();

        // Serializa a geração do mesmo modelo: duas transações (o resumo e o job, por exemplo) esperam aqui uma pela outra, e a segunda
        // já enxerga o que a primeira criou, em vez de estourar a unique de modelo + mês no commit.
        recurringExpenseRepository.findByIdForUpdate(recurringExpense.getId());

        if (recurringExpenseSkipRepository.existsByRecurringExpenseIdAndDueDate(recurringExpense.getId(), dueDate))
            return Optional.empty();

        return expenseService.createFromRecurrence(recurringExpense.toExpenseCommand(month, dueDate));
    }

    /**
     * Gera as despesas do mês corrente e do seguinte: a assinatura já aparece no mês que vem sem esperar o job. É o ponto que o cadastro
     * do modelo e o job usam, e o único que avisa o resumo mensal ({@link PeriodDataChangedEvent}): as despesas criadas mudam os
     * resumos que já existem a partir do vencimento mais antigo delas. Rodar de novo sem criar nada não avisa. {@link #materialize} e
     * {@link #materializeForUser} não avisam: o segundo roda dentro do cálculo do resumo (avisar de lá seria recursão).
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

    /**
     * Garante, para todos os modelos do usuário, as despesas que o resumo do mês enxerga. A do mês anterior entra junto: com
     * cartão, a compra do mês anterior pode vencer neste mês (a fatura fecha antes do vencimento).
     */
    @Transactional
    public void materializeForUser(UUID userId, YearMonth month) {
        recurringExpenseRepository.findByUserId(userId).forEach(recurringExpense -> {
            materialize(recurringExpense, month.minusMonths(1));
            materialize(recurringExpense, month);
        });
    }

}
