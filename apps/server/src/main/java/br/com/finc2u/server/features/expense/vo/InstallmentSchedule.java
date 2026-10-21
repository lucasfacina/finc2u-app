package br.com.finc2u.server.features.expense.vo;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;

import java.util.ArrayList;
import java.util.List;

/*
 * As linhas de uma compra parcelada: a parcela informada (k) e as seguintes (k+1..N), uma por mês. A parcela informada
 * entra como está (mesma instância); as futuras são cópias novas, sempre PENDING e sem paidAt. O vencimento da parcela
 * j é calculado a partir do primeiro (first.dueDate + (j - k) meses), nunca encadeado: 31/jan → 28/fev → 31/mar, e não
 * 28/mar. Domínio puro, sem Spring.
 *
 * Pré-condição: a despesa já passou por prepareForPersistence (vencimento, status e parcelas resolvidos e validados) e o
 * chamador definiu o installmentGroupId da compra.
 */
public record InstallmentSchedule(List<Expense> installments) {

    public InstallmentSchedule {
        installments = List.copyOf(installments);
    }

    public static InstallmentSchedule from(Expense first) {
        if (first.getExpenseType() != ExpenseType.PARCELED) {
            throw new IllegalArgumentException("Só despesas PARCELED geram parcelas.");
        }
        if (first.getInstallmentGroupId() == null) {
            throw new IllegalArgumentException("A compra parcelada precisa de um installmentGroupId.");
        }

        var installments = new ArrayList<Expense>();
        installments.add(first);

        var firstInstallment = first.getCurrentInstallment();
        for (var installment = firstInstallment + 1; installment <= first.getTotalInstallment(); installment++) {
            installments.add(futureInstallmentOf(first, installment, installment - firstInstallment));
        }

        return new InstallmentSchedule(installments);
    }

    private static Expense futureInstallmentOf(Expense first, int installment, int monthsAfterFirst) {
        return Expense.builder()
            .description(first.getDescription())
            .value(first.getValue())
            .dueDate(first.getDueDate().plusMonths(monthsAfterFirst))
            .expenseType(first.getExpenseType())
            .paymentStatus(PaymentStatus.PENDING)
            .currentInstallment(installment)
            .totalInstallment(first.getTotalInstallment())
            .installmentGroupId(first.getInstallmentGroupId())
            .user(first.getUser())
            .cardAccount(first.getCardAccount())
            .category(first.getCategory())
            .build();
    }

}
