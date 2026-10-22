package br.com.finc2u.server.shared.event;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/*
 * Publicado por quem altera dados que entram no resumo mensal (despesas, ganhos extras, salários, despesas recorrentes) para
 * avisar que o período mudou, sem conhecer o resumo. `from` é o mês mais antigo afetado: os resumos que já existem desse mês
 * em diante (inclusive) precisam ser recalculados, porque o saldo de um mês é o carryover do seguinte.
 */
public record PeriodDataChangedEvent(
    UUID userId,
    YearMonth from
) {

    // Uma alteração que mexe em mais de uma data (ex.: um vencimento que muda de mês) afeta a partir da mais antiga delas.
    public static PeriodDataChangedEvent earliestOf(UUID userId, LocalDate... dates) {
        return earliestOf(userId, Arrays.asList(dates));
    }

    public static PeriodDataChangedEvent earliestOf(UUID userId, Collection<LocalDate> dates) {
        return new PeriodDataChangedEvent(userId, YearMonth.from(Collections.min(dates)));
    }

}
