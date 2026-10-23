package br.com.finc2u.server.features.recurringexpense.entity;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.expense.vo.RecurrenceExpenseCommand;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.shared.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/*
 * Modelo de uma despesa que se repete mês a mês (assinatura, aluguel). Ele não é uma despesa: a cada mês vira uma
 * instância real (Expense), materializada à parte.
 */
@Entity
@Table(name = "tb_recurring_expense")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringExpense extends BaseModel {

    private String description;

    @Column(name = "recurring_value")
    private BigDecimal value;

    // Dia do mês do vencimento (1–31); em mês mais curto a instância cai no último dia.
    private Integer dayOfMonth;

    // O mês entra como o dia 1 dele (um YearMonth não é tipo JPA); o fim é opcional: nulo = sem término.
    private LocalDate startMonth;
    private LocalDate endMonth;

    // LAZY: nenhum Response lê o dono do modelo; evita uma consulta extra a cada listagem.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;

    // LAZY: só as consultas que montam o Response carregam a categoria (via @EntityGraph).
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Category category;

    @ManyToOne
    private CardAccount cardAccount;

    // O usuário, a categoria e o cartão são associados pelo service, que os resolve por id.
    public static RecurringExpense of(String description, BigDecimal value, Integer dayOfMonth, YearMonth startMonth,
                                      YearMonth endMonth) {
        return RecurringExpense.builder()
            .description(description)
            .value(value)
            .dayOfMonth(dayOfMonth)
            .startMonth(startMonth.atDay(1))
            .endMonth(endMonth != null
                ? endMonth.atDay(1)
                : null)
            .build();
    }

    // O modelo só gera despesa de startMonth a endMonth
    public boolean isActiveIn(YearMonth month) {
        return !month.isBefore(YearMonth.from(startMonth))
            && (endMonth == null || !month.isAfter(YearMonth.from(endMonth)));
    }

    /*
     * Vencimento da despesa do mês: o dia do modelo (em mês mais curto, o último dia). Com cartão, esse dia é o da
     * compra e o vencimento segue a fatura em que ela cai (pode ser no mês seguinte).
     */
    public LocalDate dueDateIn(YearMonth month) {
        var chargeDate = month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
        return cardAccount != null
            ? cardAccount.calculateInvoiceDueDate(chargeDate)
            : chargeDate;
    }

    /*
     * Os dados da despesa que este modelo gera no vencimento, no VO neutro que a feature expense entende (ela não conhece o
     * modelo). O vencimento chega pronto: quem chama o obtém de dueDateIn.
     */
    public RecurrenceExpenseCommand toExpenseCommand(LocalDate dueDate) {
        return new RecurrenceExpenseCommand(getId(), description, value, dueDate, user, category, cardAccount);
    }

}
