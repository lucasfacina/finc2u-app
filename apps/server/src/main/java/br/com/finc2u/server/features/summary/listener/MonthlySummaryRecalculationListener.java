package br.com.finc2u.server.features.summary.listener;

import br.com.finc2u.server.features.summary.service.MonthlySummaryService;
import br.com.finc2u.server.shared.event.PeriodDataChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Mantém os resumos mensais em dia quando os dados de um período mudam. Roda <b>antes do commit</b>, na mesma transação de quem
 * alterou os dados: se o recálculo falhar, a alteração inteira é desfeita e o resumo nunca fica defasado em relação aos dados.
 * Só recalcula os resumos que já existem (quem não tem resumo não ganha um). Sem transação em curso ({@code fallbackExecution}),
 * roda na hora, em transação própria, em vez de descartar o aviso em silêncio.
 */
@Component
@RequiredArgsConstructor
public class MonthlySummaryRecalculationListener {

    private final MonthlySummaryService monthlySummaryService;

    /**
     * Recalcula, em ordem crescente, os resumos existentes do usuário do mês {@code from} do evento em diante (inclusive).
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onPeriodDataChanged(PeriodDataChangedEvent event) {
        monthlySummaryService.recalculateExistingFrom(event.userId(), event.from());
    }

}
