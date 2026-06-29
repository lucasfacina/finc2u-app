package br.com.finc2u.server.features.salaryhistory.service;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.salaryhistory.entity.SalaryHistory;
import br.com.finc2u.server.features.salaryhistory.repository.SalaryHistoryRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalaryHistoryService {

    private final SalaryHistoryRepository salaryHistoryRepository;
    private final UserService userService;

    @Transactional
    public SalaryHistory create(SalaryHistory history, UUID userId) {
        User user = userService.getById(userId);
        if (salaryHistoryRepository.existsByUserIdAndEffectiveFrom(userId, history.getEffectiveFrom())) {
            throw new BusinessException("Já existe um registro de salário com vigência em " +
                    history.getEffectiveFrom() + " para este usuário.");
        }
        history.setUser(user);
        return salaryHistoryRepository.save(history);
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
     * Caso não exista histórico, usa o fallback (baseSalary do UserConfiguration).
     */
    @Transactional(readOnly = true)
    public BigDecimal getBaseSalaryForPeriod(UUID userId, LocalDate periodStart, BigDecimal fallback) {
        return salaryHistoryRepository
                .findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(userId, periodStart)
                .map(SalaryHistory::getBaseSalary)
                .orElse(fallback);
    }

    @Transactional
    public SalaryHistory update(UUID id, SalaryHistory historyData) {
        SalaryHistory history = getById(id);
        salaryHistoryRepository
                .findByUserIdAndEffectiveFrom(history.getUser().getId(), historyData.getEffectiveFrom())
                .filter(conflict -> !conflict.getId().equals(id))
                .ifPresent(conflict -> {
                    throw new BusinessException("Já existe um registro de salário com vigência em " + historyData.getEffectiveFrom() + " para este usuário.");
                });
        history.setBaseSalary(historyData.getBaseSalary());
        history.setEffectiveFrom(historyData.getEffectiveFrom());
        return salaryHistoryRepository.save(history);
    }

    @Transactional
    public void delete(UUID id) {
        SalaryHistory history = getById(id);
        salaryHistoryRepository.delete(history);
    }

}