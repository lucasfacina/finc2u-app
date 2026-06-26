package br.com.finc2u.server.features.summary.entity;

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

    /**
     * Aplica os totais agregados do período e deriva os campos compostos:
     * -> totalIncome     = baseSalary + totalExtras + cashBalance
     * -> remainingAmount = totalIncome − totalExpenses
     * -> flexibleBudget  = totalIncome − totalFixed
     * -> totalFixed entra apenas na derivação do orçamento flexível (não é persistido).
     */
    public void applyTotals(
            BigDecimal totalExpenses,
            BigDecimal totalPaid,
            BigDecimal totalPending,
            BigDecimal totalExtras,
            BigDecimal totalFixed,
            BigDecimal cashBalance,
            BigDecimal savingsBalance,
            BigDecimal baseSalary
    ) {
        BigDecimal totalIncome = baseSalary.add(totalExtras).add(cashBalance);

        this.totalExpenses = totalExpenses;
        this.totalPaid = totalPaid;
        this.totalPending = totalPending;
        this.totalExtras = totalExtras;
        this.cashBalance = cashBalance;
        this.savingsBalance = savingsBalance;
        this.remainingAmount = totalIncome.subtract(totalExpenses);
        this.flexibleBudget = totalIncome.subtract(totalFixed);
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
