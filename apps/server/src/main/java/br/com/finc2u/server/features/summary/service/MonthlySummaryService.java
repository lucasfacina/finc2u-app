package br.com.finc2u.server.features.summary.service;


import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import br.com.finc2u.server.features.extraincome.service.ExtraIncomeService;
import br.com.finc2u.server.features.recurringexpense.materializer.RecurringExpenseMaterializer;
import br.com.finc2u.server.features.salaryhistory.service.SalaryHistoryService;
import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.repository.MonthlySummaryRepository;
import br.com.finc2u.server.features.summary.vo.MonthlySummaryCalculator;
import br.com.finc2u.server.features.summary.vo.MonthlySummaryInput;
import br.com.finc2u.server.features.summary.vo.MonthlySummaryResult;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import br.com.finc2u.server.shared.vo.MonthlyPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MonthlySummaryService {

    private final MonthlySummaryRepository monthlySummaryRepository;
    private final ExpenseService expenseService;
    private final ExtraIncomeService extraIncomeService;
    private final UserService userService;
    private final SalaryHistoryService salaryHistoryService;
    private final RecurringExpenseMaterializer recurringExpenseMaterializer;

    /*
     * Recalcula o resumo do período e salva via upsert. Orquestra apenas: garante as despesas recorrentes do mês (um
     * mês futuro já enxerga a assinatura), lê despesas e extras do mês pelos services das suas features, recupera ou
     * cria o resumo e o mês anterior, delega o cálculo ao MonthlySummaryCalculator (puro) e grava o resultado. Não altera
     * nenhuma despesa. Devolve também se o resumo foi criado agora ou recalculado.
     */
    @Transactional
    public MonthlySummaryResult calculateOrRecalculate(UUID userId, Integer month, Integer year) {
        User user = userService.getById(userId);
        MonthlyPeriod period = MonthlyPeriod.of(month, year);

        recurringExpenseMaterializer.materializeForUser(userId, YearMonth.of(year, month));

        List<Expense> expenses = expenseService.getByUserAndPeriod(userId, period.start(), period.end());
        List<ExtraIncome> extraIncomes = extraIncomeService.getByUserAndPeriod(userId, period.start(), period.end());

        Optional<MonthlySummary> existing = monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, month, year);
        MonthlySummary summary = existing.orElseGet(() -> MonthlySummary.forPeriod(user, month, year));

        BigDecimal baseSalary = salaryHistoryService.getBaseSalaryForPeriod(userId, period.start());

        Optional<MonthlySummary> previous = findPreviousMonth(userId, period);
        summary.apply(MonthlySummaryCalculator.calculate(new MonthlySummaryInput(
            expenses,
            extraIncomes,
            baseSalary,
            user.savingsBalanceOrZero(),
            previous.map(MonthlySummary::getRemainingAmount).orElse(null),
            previous.map(MonthlySummary::getSavingsBalance).orElse(null)
        )));

        return new MonthlySummaryResult(monthlySummaryRepository.save(summary), existing.isEmpty());
    }

    @Transactional(readOnly = true)
    public List<MonthlySummary> getByUser(UUID userId) {
        return monthlySummaryRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public MonthlySummary getByUserAndPeriod(UUID userId, Integer month, Integer year) {
        return monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, month, year)
            .orElseThrow(() -> new ResourceNotFoundException("Resumo mensal não encontrado para o período informado."));
    }

    @Transactional
    public void delete(UUID userId, Integer month, Integer year) {
        MonthlySummary summary = getByUserAndPeriod(userId, month, year);
        monthlySummaryRepository.delete(summary);
    }

    private Optional<MonthlySummary> findPreviousMonth(UUID userId, MonthlyPeriod period) {
        MonthlyPeriod previous = period.previous();
        return monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, previous.month(), previous.year());
    }

}
