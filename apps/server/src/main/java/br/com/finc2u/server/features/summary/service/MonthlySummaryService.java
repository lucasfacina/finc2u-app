package br.com.finc2u.server.features.summary.service;


import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.expense.service.ExpenseService;
import br.com.finc2u.server.features.extraincome.service.ExtraIncomeService;
import br.com.finc2u.server.features.recurringexpense.materializer.RecurringExpenseMaterializer;
import br.com.finc2u.server.features.salaryhistory.service.SalaryHistoryService;
import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.entity.MonthlySummaryCategoryTotal;
import br.com.finc2u.server.features.summary.repository.MonthlySummaryCategoryTotalRepository;
import br.com.finc2u.server.features.summary.repository.MonthlySummaryRepository;
import br.com.finc2u.server.features.summary.vo.MonthlySummaryCalculator;
import br.com.finc2u.server.features.summary.vo.MonthlySummaryDetail;
import br.com.finc2u.server.features.summary.vo.MonthlySummaryInput;
import br.com.finc2u.server.features.summary.vo.MonthlySummaryResult;
import br.com.finc2u.server.features.user.service.UserService;
import br.com.finc2u.server.shared.vo.MonthlyPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MonthlySummaryService {

    private final MonthlySummaryRepository monthlySummaryRepository;
    private final MonthlySummaryCategoryTotalRepository categoryTotalRepository;
    private final ExpenseService expenseService;
    private final ExtraIncomeService extraIncomeService;
    private final UserService userService;
    private final SalaryHistoryService salaryHistoryService;
    private final RecurringExpenseMaterializer recurringExpenseMaterializer;
    private final Clock clock;

    /*
     * Recalcula o resumo do período e salva via upsert. Orquestra apenas: garante as despesas recorrentes do mês (um
     * mês futuro já enxerga a assinatura), lê despesas e extras do mês pelos services das suas features, recupera ou
     * cria o resumo e o mês anterior, delega o cálculo ao MonthlySummaryCalculator (puro) e grava o resultado. Não altera
     * nenhuma despesa. Grava também os totais por categoria (no recálculo, os antigos saem). Devolve se o resumo foi criado
     * agora ou recalculado. Calcula só o mês pedido: o carryover para os meses seguintes é do recalculateFrom.
     */
    private MonthlySummaryResult calculate(UUID userId, Integer month, Integer year) {
        var user = userService.getById(userId);
        var period = MonthlyPeriod.of(month, year);

        recurringExpenseMaterializer.materializeForUser(userId, YearMonth.of(year, month));

        var expenses = expenseService.getByUserAndPeriod(userId, period.start(), period.end());
        var extraIncomes = extraIncomeService.getByUserAndPeriod(userId, period.start(), period.end());

        var existing = monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, month, year);
        var summary = existing.orElseGet(() -> MonthlySummary.forPeriod(user, month, year));

        var baseSalary = salaryHistoryService.getBaseSalaryForPeriod(userId, period.start());

        var previous = findPreviousMonth(userId, period);
        var figures = MonthlySummaryCalculator.calculate(new MonthlySummaryInput(
            expenses,
            extraIncomes,
            LocalDate.now(clock),
            baseSalary,
            user.savingsBalanceOrZero(),
            previous.map(MonthlySummary::getRemainingAmount).orElse(null),
            previous.map(MonthlySummary::getSavingsBalance).orElse(null)
        ));
        summary.apply(figures);

        var saved = monthlySummaryRepository.save(summary);
        if (existing.isPresent()) {
            categoryTotalRepository.deleteBySummaryId(saved.getId());
        }

        var categoryTotals = categoryTotalRepository.saveAll(figures.categoryTotals().stream()
            .map(total -> MonthlySummaryCategoryTotal.of(saved, total.category(), total.total()))
            .toList());

        return new MonthlySummaryResult(saved, categoryTotals, existing.isEmpty());
    }

    // Um mês só, sem propagar o carryover (o recalculateFrom faz isso); a transação é deste ponto de entrada.
    @Transactional
    public MonthlySummaryResult calculateOrRecalculate(UUID userId, Integer month, Integer year) {
        return calculate(userId, month, year);
    }

    /*
     * Calcula o mês pedido (criando o resumo se ele não existir) e, em ordem crescente, os resumos que já existem nos meses
     * seguintes: o remainingAmount de um mês é o cashBalance do próximo, então cada um lê o anterior já atualizado. Não cria
     * resumo de mês que não existe; se há uma lacuna entre dois meses, o seguinte a ela fica com previousMonthMissing.
     * Devolve o resultado do mês pedido.
     */
    @Transactional
    public MonthlySummaryResult recalculateFrom(UUID userId, YearMonth from) {
        var result = calculate(userId, from.getMonthValue(), from.getYear());
        recalculateExistingFrom(userId, from.plusMonths(1));
        return result;
    }

    // O histórico lê os totais por categoria de todos os resumos numa consulta só (sem uma por resumo).
    @Transactional(readOnly = true)
    public List<MonthlySummaryDetail> getByUser(UUID userId) {
        var summaries = monthlySummaryRepository.findByUserId(userId);
        var categoryTotalsBySummary = categoryTotalsOf(summaries);
        return summaries.stream()
            .map(summary -> new MonthlySummaryDetail(summary, categoryTotalsBySummary.getOrDefault(summary.getId(), List.of())))
            .toList();
    }

    @Transactional(readOnly = true)
    public MonthlySummaryDetail getByUserAndPeriod(UUID userId, Integer month, Integer year) {
        var summary = findByPeriod(userId, month, year);
        return new MonthlySummaryDetail(summary, categoryTotalsOf(List.of(summary)).getOrDefault(summary.getId(), List.of()));
    }

    // Os totais por categoria saem antes do resumo (FK); os resumos existentes dos meses seguintes perdem o carryover dele.
    @Transactional
    public void delete(UUID userId, Integer month, Integer year) {
        var summary = findByPeriod(userId, month, year);
        categoryTotalRepository.deleteBySummaryId(summary.getId());
        monthlySummaryRepository.delete(summary);
        recalculateExistingFrom(userId, YearMonth.of(year, month).plusMonths(1));
    }

    /*
     * Recalcula, em ordem crescente, só os resumos que já existem do mês informado em diante (inclusive); nunca cria um. É o
     * que o aviso de alteração de dados chama: quem não tem resumo não faz nada. A consulta antes de cada cálculo grava o
     * que o mês anterior mudou.
     */
    @Transactional
    public void recalculateExistingFrom(UUID userId, YearMonth first) {
        monthlySummaryRepository.findFromPeriod(userId, first.getYear(), first.getMonthValue())
            .forEach(summary -> calculate(userId, summary.getMonth(), summary.getYear()));
    }

    private MonthlySummary findByPeriod(UUID userId, Integer month, Integer year) {
        return monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, month, year)
            .orElseThrow(() -> new ResourceNotFoundException("Resumo mensal não encontrado para o período informado."));
    }

    private Map<UUID, List<MonthlySummaryCategoryTotal>> categoryTotalsOf(List<MonthlySummary> summaries) {
        if (summaries.isEmpty()) return Map.of();

        return categoryTotalRepository.findBySummaryIdInOrderByCategoryNameAsc(summaries.stream().map(MonthlySummary::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(categoryTotal -> categoryTotal.getSummary().getId()));
    }

    private Optional<MonthlySummary> findPreviousMonth(UUID userId, MonthlyPeriod period) {
        var previous = period.previous();
        return monthlySummaryRepository.findByUserIdAndMonthAndYear(userId, previous.month(), previous.year());
    }

}
