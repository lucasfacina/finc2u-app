package br.com.finc2u.server.features.summary.form;

import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.entity.MonthlySummaryCategoryTotal;
import br.com.finc2u.server.features.summary.vo.MonthlySummaryDetail;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Resumo financeiro de um mês do usuário.")
public record MonthlySummaryResponse(

    @Schema(
        description = "Id do usuário dono do resumo.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    UUID userId,

    @Schema(
        description = "Mês do resumo (1 a 12).",
        example = "10"
    )
    Integer month,

    @Schema(
        description = "Ano do resumo.",
        example = "2026"
    )
    Integer year,

    @Schema(
        description = "Salário vigente no período; zero antes da primeira vigência.",
        example = "3000.00"
    )
    BigDecimal baseSalary,

    @Schema(
        description = "Renda do mês: baseSalary + totalExtras + cashBalance.",
        example = "4200.00"
    )
    BigDecimal totalIncome,

    @Schema(
        description = "Soma das despesas do mês, pagas e pendentes.",
        example = "2500.00"
    )
    BigDecimal totalExpenses,

    @Schema(
        description = "Soma das despesas pagas.",
        example = "1200.00"
    )
    BigDecimal totalPaid,

    @Schema(
        description = "Soma das despesas pendentes.",
        example = "1300.00"
    )
    BigDecimal totalPending,

    @Schema(
        description = "Soma das despesas pendentes com vencimento anterior à data do cálculo.",
        example = "0.00"
    )
    BigDecimal totalOverdue,

    @Schema(
        description = "Soma das despesas do tipo FIXED, pagas ou não.",
        example = "400.00"
    )
    BigDecimal totalFixed,

    @Schema(
        description = "Soma dos ganhos extras do mês.",
        example = "1200.00"
    )
    BigDecimal totalExtras,

    @Schema(
        description = "Sobra do mês anterior (carryover); zero se não houver resumo anterior.",
        example = "0.00"
    )
    BigDecimal cashBalance,

    @Schema(
        description = "true quando não há resumo do mês anterior: o cashBalance zero vem dessa falta, não de um saldo.",
        example = "false"
    )
    Boolean previousMonthMissing,

    @Schema(
        description = "Saldo da poupança do usuário no momento do cálculo.",
        example = "5000.00"
    )
    BigDecimal savingsBalance,

    @Schema(
        description = "Poupança atual menos a do mês anterior; zero se não houver anterior.",
        example = "0.00"
    )
    BigDecimal savingsYield,

    @Schema(
        description = "Quanto sobra ao fim do mês: totalIncome menos totalExpenses.",
        example = "1700.00"
    )
    BigDecimal remainingAmount,

    @Schema(description = "Quanto foi gasto em cada categoria (pago + pendente), ordenado pelo nome.")
    List<CategoryTotalResponse> categoryTotals,

    @Schema(
        description = "Momento do último cálculo.",
        example = "2026-10-15T09:30:00"
    )
    LocalDateTime updatedAt

) {

    public static MonthlySummaryResponse from(MonthlySummaryDetail detail) {
        return from(detail.summary(), detail.categoryTotals());
    }

    public static MonthlySummaryResponse from(MonthlySummary summary, List<MonthlySummaryCategoryTotal> categoryTotals) {
        return new MonthlySummaryResponse(
            summary.getUser().getId(),
            summary.getMonth(),
            summary.getYear(),
            summary.getBaseSalary(),
            summary.getTotalIncome(),
            summary.getTotalExpenses(),
            summary.getTotalPaid(),
            summary.getTotalPending(),
            summary.getTotalOverdue(),
            summary.getTotalFixed(),
            summary.getTotalExtras(),
            summary.getCashBalance(),
            summary.getPreviousMonthMissing(),
            summary.getSavingsBalance(),
            summary.getSavingsYield(),
            summary.getRemainingAmount(),
            categoryTotals.stream().map(CategoryTotalResponse::from).toList(),
            summary.getUpdatedAt()
        );
    }

}
