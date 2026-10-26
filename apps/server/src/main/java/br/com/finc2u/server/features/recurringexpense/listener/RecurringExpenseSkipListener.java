package br.com.finc2u.server.features.recurringexpense.listener;

import br.com.finc2u.server.features.expense.event.GeneratedExpenseDeletedEvent;
import br.com.finc2u.server.features.recurringexpense.service.RecurringExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Registra o mês como pulado quando o usuário apaga uma despesa que o modelo gerou. Roda de forma síncrona, na mesma
 * transação de quem apagou: se falhar, a exclusão inteira é desfeita e a despesa não volta sozinha depois. Reage ao
 * {@code GeneratedExpenseDeletedEvent}, então o {@code ExpenseService} não conhece o modelo recorrente.
 */
@Component
@RequiredArgsConstructor
public class RecurringExpenseSkipListener {

    private final RecurringExpenseService recurringExpenseService;

    /**
     * Marca o mês do modelo como pulado, para o materializador não recriar a despesa (nem no resumo, nem pelo job).
     */
    @EventListener
    public void onGeneratedExpenseDeleted(GeneratedExpenseDeletedEvent event) {
        recurringExpenseService.skipMonth(event.recurringExpenseId(), event.recurrenceMonth());
    }

}
