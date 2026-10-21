package br.com.finc2u.server.features.summary.vo;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;

import java.math.BigDecimal;
import java.util.List;

/*
 * Tudo o que o cálculo do resumo precisa, já lido pelo service: as despesas e os ganhos do mês, o salário vigente no mês e
 * o saldo da poupança do usuário. Os dois últimos valores vêm do resumo do mês anterior e são nulos quando ele não existe.
 */
public record MonthlySummaryInput(
    List<Expense> expenses,
    List<ExtraIncome> extraIncomes,
    BigDecimal baseSalary,
    BigDecimal savingsBalance,
    BigDecimal previousRemainingAmount,
    BigDecimal previousSavingsBalance
) {
}
