package br.com.finc2u.server.features.summary.service;


import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import br.com.finc2u.server.features.extra.entity.Extra;
import br.com.finc2u.server.features.extra.repository.ExtraRepository;
import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.repository.MonthlySummaryRepository;
import br.com.finc2u.server.features.summary.vo.MonthlySummaryTotals;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.salaryhistory.service.SalaryHistoryService;
import br.com.finc2u.server.features.user.service.UserService;
import br.com.finc2u.server.shared.vo.MonthlyPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MonthlySummaryService {

    private final MonthlySummaryRepository monthlySummaryRepository;
    private final ExpenseRepository expenseRepository;
    private final ExtraRepository extraRepository;
    private final UserService userService;
    private final SalaryHistoryService salaryHistoryService;

    /*
     * Recalcula o resumo do período e salva via upsert. Orquestra apenas: busca despesas/extras do mês,
     * reconcilia despesas vencidas, agrega os totais (MonthlySummaryTotals), recupera ou cria o
     * resumo + o restante do mês anterior, e delega o cálculo à própria entidade.
     */
    @Transactional
    public MonthlySummary calculateOrRecalculate(UUID userId, Integer month, Integer year) {
        User user = userService.getById(userId);
        MonthlyPeriod period = MonthlyPeriod.of(month, year);

        List<Expense> expenses = expenseRepository.findByUserIdAndDueDateBetween(userId, period.start(), period.end());
        List<Extra> extras = extraRepository.findByUserIdAndDateBetween(userId, period.start(), period.end());

        expenses.forEach(Expense::markPaidIfOverdue);

        MonthlySummaryTotals totals = MonthlySummaryTotals.from(expenses, extras);

        MonthlySummary summary = monthlySummaryRepository.findByUserIdAndIdMonthAndIdYear(userId, month, year)
                .orElseGet(() -> MonthlySummary.userIdForPeriod(user, month, year));

        BigDecimal baseSalary = salaryHistoryService.getBaseSalaryForPeriod(userId, period.start(), user.baseSalaryOrZero());

        Optional<MonthlySummary> previous = findPreviousMonth(userId, period);
        summary.resolveTotals(
                totals,
                previous.map(MonthlySummary::getRemainingAmount).orElse(null),
                previous.map(MonthlySummary::getSavingsBalance).orElse(null),
                baseSalary
        );

        return monthlySummaryRepository.save(summary);
    }

    @Transactional(readOnly = true)
    public List<MonthlySummary> getByUser(UUID userId) {
        return monthlySummaryRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public MonthlySummary getByUserAndPeriod(UUID userId, Integer month, Integer year) {
        return monthlySummaryRepository.findByUserIdAndIdMonthAndIdYear(userId, month, year)
                .orElseThrow(() -> new ResourceNotFoundException("Resumo mensal não encontrado para o período informado."));
    }

    @Transactional
    public void delete(UUID userId, Integer month, Integer year) {
        MonthlySummary summary = getByUserAndPeriod(userId, month, year);
        monthlySummaryRepository.delete(summary);
    }

    private Optional<MonthlySummary> findPreviousMonth(UUID userId, MonthlyPeriod period) {
        MonthlyPeriod previous = period.previous();
        return monthlySummaryRepository.findByUserIdAndIdMonthAndIdYear(userId, previous.month(), previous.year());
    }

}
