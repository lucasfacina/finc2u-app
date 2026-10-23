package br.com.finc2u.server.features.salaryhistory.listener;

import br.com.finc2u.server.features.salaryhistory.entity.SalaryHistory;
import br.com.finc2u.server.features.salaryhistory.service.SalaryHistoryService;
import br.com.finc2u.server.features.user.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Cria a primeira vigência de salário do usuário recém-cadastrado. Roda de forma síncrona, na mesma transação do cadastro: se
 * falhar, o cadastro inteiro é desfeito e o usuário não fica sem salário. O salário e a data de vigência chegam no
 * {@code UserRegisteredEvent}, então a feature {@code user} não depende desta.
 */
@Component
@RequiredArgsConstructor
public class FirstSalaryListener {

    private final SalaryHistoryService salaryHistoryService;

    /**
     * Grava o primeiro salário do usuário do evento. Como qualquer vigência, isso avisa o resumo mensal (que ainda não existe para um
     * usuário novo, então nada é recalculado).
     */
    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        var firstSalary = SalaryHistory.builder()
            .baseSalary(event.baseSalary())
            .effectiveFrom(event.salaryEffectiveFrom())
            .build();

        salaryHistoryService.create(firstSalary, event.user().getId());
    }

}
