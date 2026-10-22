package br.com.finc2u.server.features.user.port;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/*
 * O que o user precisa saber do salário, declarado por ele mesmo: o salário vigente vive em outra feature (salaryhistory),
 * que implementa esta porta. Assim o user depende só da abstração e a dependência aponta de salaryhistory para user, nunca
 * o contrário (sem ciclo entre as duas features).
 */
public interface CurrentSalaryPort {

    // Salário com vigência mais recente até hoje; vazio se o usuário ainda não tem nenhuma vigência iniciada.
    Optional<BigDecimal> getCurrentBaseSalary(UUID userId);

    // O mesmo para vários usuários por uma consulta só (evita N+1 na listagem); quem não tem vigência não aparece no mapa.
    Map<UUID, BigDecimal> getCurrentBaseSalaries(Collection<UUID> userIds);

}
