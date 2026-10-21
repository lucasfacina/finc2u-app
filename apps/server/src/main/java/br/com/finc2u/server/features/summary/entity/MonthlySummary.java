package br.com.finc2u.server.features.summary.entity;

import br.com.finc2u.server.features.summary.vo.MonthlySummaryFigures;
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
    private BigDecimal totalOverdue;
    private BigDecimal totalFixed;
    private BigDecimal totalExtras;
    private BigDecimal cashBalance;
    // Boolean (não boolean): resumos anteriores à coluna ficam null até o próximo recálculo.
    private Boolean previousMonthMissing;
    private BigDecimal savingsBalance;
    private BigDecimal savingsYield;
    private BigDecimal remainingAmount;

    // LAZY: o MonthlySummaryResponse lê só o id do dono (o id de um proxy não gera consulta).
    @ManyToOne(fetch = FetchType.LAZY)
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

    // Grava os números calculados (MonthlySummaryCalculator); a entidade não calcula, só guarda o resumo do período.
    public void apply(MonthlySummaryFigures figures) {
        this.baseSalary = figures.baseSalary();
        this.totalIncome = figures.totalIncome();
        this.totalExpenses = figures.totalExpenses();
        this.totalPaid = figures.totalPaid();
        this.totalPending = figures.totalPending();
        this.totalOverdue = figures.totalOverdue();
        this.totalFixed = figures.totalFixed();
        this.totalExtras = figures.totalExtras();
        this.cashBalance = figures.cashBalance();
        this.previousMonthMissing = figures.previousMonthMissing();
        this.savingsBalance = figures.savingsBalance();
        this.savingsYield = figures.savingsYield();
        this.remainingAmount = figures.remainingAmount();
    }

}
