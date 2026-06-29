package br.com.finc2u.server.features.summary.entity;

import br.com.finc2u.server.features.summary.vo.MonthlySummaryTotals;
import br.com.finc2u.server.features.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "tb_monthly_summary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlySummary {

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
    private BigDecimal flexibleBudget;

    @EmbeddedId
    private MonthlySummaryId id;

    @ManyToOne
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Integer getMonth() {
        return id != null ? id.getMonth() : null;
    }

    public Integer getYear() {
        return id != null ? id.getYear() : null;
    }

    /**
     * Cria um resumo novo (id composto + usuário) para o período, ainda não calculado.
     */
    public static MonthlySummary userIdForPeriod(User user, Integer month, Integer year) {
        return MonthlySummary.builder()
                .id(MonthlySummaryId.builder()
                        .userId(user.getId())
                        .month(month)
                        .year(year)
                        .build())
                .user(user)
                .build();
    }

    /*
     * Recalcula o resumo do período: resolve o caixa, tira o snapshot da poupança e deriva os campos
     * compostos a partir dos totais agregados.
     * -> baseSalary      = salário vigente no período (resolvido pelo service via SalaryHistory; fallback UserConfiguration)
     * -> cashBalance     = remainingAmount do mês anterior (carryover automático); zero se não houver
     * -> totalIncome     = baseSalary + totalExtras + cashBalance
     * -> remainingAmount = totalIncome − totalExpenses  (projeção: quanto sobra ao fim do mês, descontando pago + pendente)
     * -> flexibleBudget  = totalIncome − totalPaid      (quanto ainda está disponível agora, só desconta o que já saiu)
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
        this.flexibleBudget = this.totalIncome.subtract(totals.totalPaid());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        MonthlySummary other = (MonthlySummary) o;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}
