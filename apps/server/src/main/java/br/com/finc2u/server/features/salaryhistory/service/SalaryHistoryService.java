package br.com.finc2u.server.features.salaryhistory.service;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.salaryhistory.entity.SalaryHistory;
import br.com.finc2u.server.features.salaryhistory.repository.SalaryHistoryRepository;
import br.com.finc2u.server.features.salaryhistory.vo.CurrentSalary;
import br.com.finc2u.server.features.user.port.CurrentSalaryPort;
import br.com.finc2u.server.features.user.service.UserService;
import br.com.finc2u.server.shared.event.PeriodDataChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Histórico de salários: cada registro vale a partir da sua data de vigência ({@code effectiveFrom}), então recalcular um mês
 * antigo usa o salário que valia naquele período, não o atual. É a fonte única do salário. Implementa a
 * {@link CurrentSalaryPort}, que a feature {@code user} declara para mostrar o salário vigente sem depender desta feature.
 * <p>
 * Toda escrita publica o {@code PeriodDataChangedEvent} a partir do mês da vigência, para o resumo mensal se manter em dia.
 */
@Service
@RequiredArgsConstructor
public class SalaryHistoryService implements CurrentSalaryPort {

    private final SalaryHistoryRepository salaryHistoryRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Registra uma vigência de salário para o usuário.
     *
     * @throws ResourceNotFoundException se o usuário não existir (lançada por {@code UserService#getById})
     * @throws BusinessException         se o usuário já tem uma vigência nessa data
     */
    @Transactional
    public SalaryHistory create(SalaryHistory history, UUID userId) {
        var user = userService.getById(userId);

        if (salaryHistoryRepository.existsByUserIdAndEffectiveFrom(userId, history.getEffectiveFrom())) {
            throw new BusinessException("Já existe um registro de salário com vigência em " +
                history.getEffectiveFrom() + " para este usuário.");
        }

        history.setUser(user);

        var saved = salaryHistoryRepository.save(history);
        eventPublisher.publishEvent(PeriodDataChangedEvent.earliestOf(userId, history.getEffectiveFrom()));

        return saved;
    }

    /**
     * Busca a vigência pelo id.
     *
     * @throws ResourceNotFoundException se não existir
     */
    @Transactional(readOnly = true)
    public SalaryHistory getById(UUID id) {
        return salaryHistoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Histórico de salário não encontrado com o ID: " + id));
    }

    /**
     * Vigências do usuário, da mais recente para a mais antiga. Sem nenhuma, devolve lista vazia (inclusive para um usuário que não
     * existe: aqui não há checagem).
     */
    @Transactional(readOnly = true)
    public List<SalaryHistory> getByUser(UUID userId) {
        return salaryHistoryRepository.findByUserIdOrderByEffectiveFromDesc(userId);
    }

    /**
     * Salário vigente no início do período: a vigência mais recente com {@code effectiveFrom} até essa data.
     *
     * @return o salário, ou zero se o período é anterior à primeira vigência
     */
    @Transactional(readOnly = true)
    public BigDecimal getBaseSalaryForPeriod(UUID userId, LocalDate periodStart) {
        return salaryHistoryRepository
            .findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(userId, periodStart)
            .map(SalaryHistory::getBaseSalary)
            .orElse(BigDecimal.ZERO);
    }

    /**
     * Salário com a vigência mais recente que já começou (até hoje, pelo {@link Clock}).
     *
     * @return vazio se o usuário ainda não tem nenhuma vigência iniciada
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> getCurrentBaseSalary(UUID userId) {
        return Optional.ofNullable(getCurrentBaseSalaries(List.of(userId)).get(userId));
    }

    /**
     * Salário vigente de vários usuários numa única consulta (evita uma consulta por usuário na listagem). Usuários sem vigência
     * iniciada não aparecem no mapa.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> getCurrentBaseSalaries(Collection<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();

        return salaryHistoryRepository.findCurrentSalaries(userIds, LocalDate.now(clock))
            .stream()
            .collect(Collectors.toMap(CurrentSalary::userId, CurrentSalary::baseSalary));
    }

    /**
     * Altera o salário e a data de vigência. O resumo é avisado a partir do mês mais antigo entre a vigência antiga e a nova.
     *
     * @throws ResourceNotFoundException se a vigência não existir (lançada por {@code SalaryHistoryService#getById})
     * @throws BusinessException         se outra vigência do usuário já tem a nova data
     */
    @Transactional
    public SalaryHistory update(UUID id, SalaryHistory historyData) {
        var salaryHistory = getById(id);
        var previousEffectiveFrom = salaryHistory.getEffectiveFrom();

        salaryHistoryRepository
            .findByUserIdAndEffectiveFrom(salaryHistory.getUser().getId(), historyData.getEffectiveFrom())
            .filter(conflict -> !conflict.getId().equals(id))
            .ifPresent(conflict -> {
                throw new BusinessException("Já existe um registro de salário com vigência em " +
                    historyData.getEffectiveFrom() + " para este usuário.");
            });

        salaryHistory.setBaseSalary(historyData.getBaseSalary());
        salaryHistory.setEffectiveFrom(historyData.getEffectiveFrom());

        var saved = salaryHistoryRepository.save(salaryHistory);
        eventPublisher.publishEvent(PeriodDataChangedEvent.earliestOf(
            salaryHistory.getUser().getId(), previousEffectiveFrom, salaryHistory.getEffectiveFrom()));

        return saved;
    }

    /**
     * Apaga a vigência e avisa o resumo a partir do mês dela.
     *
     * @throws ResourceNotFoundException se não existir (lançada por {@code SalaryHistoryService#getById})
     */
    @Transactional
    public void delete(UUID id) {
        var history = getById(id);
        salaryHistoryRepository.delete(history);
        eventPublisher.publishEvent(PeriodDataChangedEvent.earliestOf(history.getUser().getId(), history.getEffectiveFrom()));
    }

}
