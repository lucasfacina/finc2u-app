package br.com.finc2u.server.features.recurringexpense.job;

import br.com.finc2u.server.features.recurringexpense.materializer.RecurringExpenseMaterializer;
import br.com.finc2u.server.features.recurringexpense.service.RecurringExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;

/**
 * Gera, mês a mês, as despesas dos modelos recorrentes ativos (mês corrente e seguinte), para a assinatura aparecer sem que
 * ninguém abra o resumo. Só é criado com {@code app.scheduling.enabled=true}. É um gatilho fino: a regra de quais meses gerar e a
 * idempotência são do {@link RecurringExpenseMaterializer}, então rodar de novo (ou rodar depois de uma falha) não duplica.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true")
public class RecurringExpenseJob {

    private final RecurringExpenseService recurringExpenseService;
    private final RecurringExpenseMaterializer recurringExpenseMaterializer;
    private final Clock clock;

    /**
     * Roda no dia 1 de cada mês, às 03:00, no fuso do {@link Clock}. Um modelo com erro não derruba os demais: a falha vai para o log e o
     * job segue. Também registra no log quantos modelos ativos processou.
     */
    @Scheduled(cron = "0 0 3 1 * *", zone = "#{@clock.zone.id}")
    public void materializeActiveRecurringExpenses() {
        var recurringExpenses = recurringExpenseService.getActiveIn(YearMonth.now(clock));

        // Um modelo com problema não impede os demais: o erro vai para o log e o job segue.
        recurringExpenses.forEach(recurringExpense -> {
            try {
                recurringExpenseMaterializer.materializeUpcoming(recurringExpense);
            } catch (RuntimeException exception) {
                log.error("Falha ao gerar as despesas do modelo recorrente {}.", recurringExpense.getId(), exception);
            }
        });

        log.info("Despesas recorrentes: {} modelo(s) ativo(s) processado(s).", recurringExpenses.size());
    }

}
