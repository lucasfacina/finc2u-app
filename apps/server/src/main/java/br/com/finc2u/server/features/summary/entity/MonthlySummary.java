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

    private BigDecimal totalExpenses;
    private BigDecimal totalPaid;
    private BigDecimal totalPending;
    private BigDecimal totalExtras;
    private BigDecimal cashBalance;
    private BigDecimal savingsBalance;
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

    /** Cria um resumo novo (id composto + usuário) para o período, ainda não calculado. */
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

    /**
     * Resolve o caixa do período: usa o valor informado se houver; senão preserva o já persistido neste
     * resumo; senão faz auto-fill com o restante do mês anterior (zero se não houver).
     */
    private BigDecimal resolveCashBalance(BigDecimal requested, BigDecimal previousMonthRemaining) {
        if (requested != null) return requested;
        if (this.cashBalance != null) return this.cashBalance;

        return previousMonthRemaining != null
                ? previousMonthRemaining
                : BigDecimal.ZERO;
    }

    /*
     * Recalcula o resumo do período: resolve o caixa, tira o snapshot da poupança e deriva os campos
     * compostos a partir dos totais agregados.
     * -> totalIncome     = baseSalary + totalExtras  (cashBalance é só acompanhamento, não compõe renda)
     * -> remainingAmount = totalIncome − totalExpenses
     * -> flexibleBudget  = totalIncome − totalExpenses  (quanto ainda pode gastar com base em tudo gasto)
     * -> baseSalary e savingsBalance vêm da entidade User associado (acessores null-safe).
     * -> previousMonthRemaining restante do mês anterior, usado no auto-fill do caixa (pode ser nulo).
     */
    public void resolveTotals(
            MonthlySummaryTotals totals,
            BigDecimal requestedCashBalance,
            BigDecimal previousMonthRemaining
    ) {
        this.cashBalance = resolveCashBalance(requestedCashBalance, previousMonthRemaining);
        this.savingsBalance = user.savingsBalanceOrZero();

        BigDecimal totalIncome = user.baseSalaryOrZero().add(totals.totalExtras());

        this.totalExpenses = totals.totalExpenses();
        this.totalPaid = totals.totalPaid();
        this.totalPending = totals.totalPending();
        this.totalExtras = totals.totalExtras();
        this.remainingAmount = totalIncome.subtract(totals.totalExpenses());
        this.flexibleBudget = totalIncome.subtract(totals.totalExpenses());
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
