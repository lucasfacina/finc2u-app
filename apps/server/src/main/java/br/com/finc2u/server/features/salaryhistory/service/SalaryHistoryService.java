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

@Service
@RequiredArgsConstructor
public class SalaryHistoryService implements CurrentSalaryPort {

    private final SalaryHistoryRepository salaryHistoryRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

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

    @Transactional(readOnly = true)
    public SalaryHistory getById(UUID id) {
        return salaryHistoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Histórico de salário não encontrado com o ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<SalaryHistory> getByUser(UUID userId) {
        return salaryHistoryRepository.findByUserIdOrderByEffectiveFromDesc(userId);
    }

    /*
     * Retorna o salário vigente no início do período informado.
     * Busca a entrada mais recente com effectiveFrom <= periodStart.
     * Caso não exista histórico (período anterior à primeira vigência), devolve zero.
     */
    @Transactional(readOnly = true)
    public BigDecimal getBaseSalaryForPeriod(UUID userId, LocalDate periodStart) {
        return salaryHistoryRepository
            .findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(userId, periodStart)
            .map(SalaryHistory::getBaseSalary)
            .orElse(BigDecimal.ZERO);
    }

    /*
     * Salário com vigência mais recente com effectiveFrom <= hoje.
     * Será vazio se o usuário ainda não tem nenhuma vigência iniciada.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> getCurrentBaseSalary(UUID userId) {
        return Optional.ofNullable(getCurrentBaseSalaries(List.of(userId)).get(userId));
    }

    /*
     * Salário vigente de vários usuários por uma única consulta (evita N+1 na listagem).
     * Usuários sem vigência iniciada não aparecem no mapa.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> getCurrentBaseSalaries(Collection<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();

        return salaryHistoryRepository.findCurrentSalaries(userIds, LocalDate.now(clock))
            .stream()
            .collect(Collectors.toMap(CurrentSalary::userId, CurrentSalary::baseSalary));
    }

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

    @Transactional
    public void delete(UUID id) {
        var history = getById(id);
        salaryHistoryRepository.delete(history);
        eventPublisher.publishEvent(PeriodDataChangedEvent.earliestOf(history.getUser().getId(), history.getEffectiveFrom()));
    }

}
