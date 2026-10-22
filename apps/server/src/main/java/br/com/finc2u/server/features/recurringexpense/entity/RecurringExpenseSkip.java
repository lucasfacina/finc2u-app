package br.com.finc2u.server.features.recurringexpense.entity;

import br.com.finc2u.server.shared.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/*
 * Um vencimento que o usuário apagou de propósito: o modelo não gera a despesa dele de novo (nem no resumo, nem pelo job).
 * Existe uma vez só por modelo e vencimento; apagar o modelo apaga os seus pulos.
 */
@Entity
@Table(
    name = "tb_recurring_expense_skip",
    uniqueConstraints = @UniqueConstraint(name = "uk_skip_recurring_due_date", columnNames = {"recurring_expense_id", "due_date"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringExpenseSkip extends BaseModel {

    private LocalDate dueDate;

    // LAZY: ninguém lê o modelo a partir do pulo (as consultas filtram pelo id dele).
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private RecurringExpense recurringExpense;

    public static RecurringExpenseSkip of(RecurringExpense recurringExpense, LocalDate dueDate) {
        return RecurringExpenseSkip.builder()
            .recurringExpense(recurringExpense)
            .dueDate(dueDate)
            .build();
    }

}
