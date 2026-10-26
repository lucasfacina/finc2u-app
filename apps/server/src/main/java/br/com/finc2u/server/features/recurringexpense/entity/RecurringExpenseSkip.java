package br.com.finc2u.server.features.recurringexpense.entity;

import br.com.finc2u.server.shared.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.YearMonth;

/*
 * Um mês que o usuário apagou de propósito: o modelo não gera a despesa dele de novo (nem no resumo, nem pelo job), qualquer que
 * fosse o vencimento. Existe uma vez só por modelo e mês; apagar o modelo apaga os seus pulos.
 */
@Entity
@Table(
    name = "tb_recurring_expense_skip",
    uniqueConstraints = @UniqueConstraint(name = "uk_skip_recurring_month", columnNames = {"recurring_expense_id", "recurrence_month"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringExpenseSkip extends BaseModel {

    // Sempre o dia 1 do mês pulado, no padrão de Expense.recurrenceMonth.
    private LocalDate recurrenceMonth;

    // LAZY: ninguém lê o modelo a partir do pulo (as consultas filtram pelo id dele).
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private RecurringExpense recurringExpense;

    public static RecurringExpenseSkip of(RecurringExpense recurringExpense, YearMonth recurrenceMonth) {
        return RecurringExpenseSkip.builder()
            .recurringExpense(recurringExpense)
            .recurrenceMonth(recurrenceMonth.atDay(1))
            .build();
    }

}
