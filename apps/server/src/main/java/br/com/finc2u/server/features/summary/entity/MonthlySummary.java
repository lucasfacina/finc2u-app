package br.com.finc2u.server.features.summary.entity;

import br.com.finc2u.server.features.summary.vo.MonthlySummaryTotals;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.shared.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
    name = "tb_monthly_summary",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "summary_month", "summary_year"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlySummary extends BaseModel {

    @Column(name = "summary_month", nullable = false)
    private Integer month;

    @Column(name = "summary_year", nullable = false)
    private Integer year;

    private BigDecimal baseSalary;
    private BigDecimal totalIncome;
    private BigDecimal totalExpenses;
    private BigDecimal totalPaid;
    private BigDecimal totalPending;
    private BigDecimal totalExtras;
    private BigDecimal cashBalance;
    private BigDecimal savingsBalance;
    private BigDecimal savingsYield;
    private BigDecimal remainingAmount;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Cria um resumo novo para o período, ainda não calculado.
    public static MonthlySummary forPeriod(User user, Integer month, Integer year) {
        return MonthlySummary.builder()
            .user(user)
            .month(month)
            .year(year)
            .build();
    }

    /*
     * Recalcula o resumo do período: resolve o caixa, tira o snapshot da poupança e deriva os campos
     * compostos a partir dos totais agregados.
     * -> baseSalary      = salário vigente no período (resolvido pelo service via SalaryHistory; zero antes da primeira vigência)
     * -> cashBalance     = remainingAmount do mês anterior (carryover automático); zero se não houver
     * -> totalIncome     = baseSalary + totalExtras + cashBalance
     * -> remainingAmount = totalIncome − totalExpenses (quanto sobra ao fim do mês, descontando pago + pendente)
     * -> savingsYield    = savingsBalance atual − savingsBalance do mês anterior; zero se não houver anterior
     * -> savingsBalance vem do UserConfiguration (snapshot no momento do cálculo).
     */
    public void resolveTotals(
        MonthlySummaryTotals totals,
        BigDecimal previousMonthRemaining,
        BigDecimal previousMonthSavingsBalance,
        BigDecimal baseSalaryForPeriod
    ) {
        this.cashBalance = previousMonthRemaining != null
            ? previousMonthRemaining
            : BigDecimal.ZERO;

        this.savingsBalance = user.savingsBalanceOrZero();

        this.savingsYield = previousMonthSavingsBalance != null
            ? this.savingsBalance.subtract(previousMonthSavingsBalance)
            : BigDecimal.ZERO;

        this.baseSalary = baseSalaryForPeriod;

        this.totalIncome = this.baseSalary.add(totals.totalExtras()).add(this.cashBalance);

        this.totalExpenses = totals.totalExpenses();
        this.totalPaid = totals.totalPaid();
        this.totalPending = totals.totalPending();
        this.totalExtras = totals.totalExtras();
        this.remainingAmount = this.totalIncome.subtract(totals.totalExpenses());
    }

}
