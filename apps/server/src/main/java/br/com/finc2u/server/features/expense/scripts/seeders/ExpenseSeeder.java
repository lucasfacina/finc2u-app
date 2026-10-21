package br.com.finc2u.server.features.expense.scripts.seeders;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import br.com.finc2u.server.features.expense.vo.InstallmentSchedule;
import br.com.finc2u.server.shared.seed.SeedContext;
import br.com.finc2u.server.shared.seed.Seeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/*
 * Despesas de dev relativas ao mês corrente (do Clock): o mês de hoje sempre tem dados. Netflix e Vivo não estão aqui
 * porque são despesas recorrentes (RecurringExpenseSeeder). A compra parcelada em curso entra como linhas reais, uma por
 * parcela restante, e há uma despesa vencida e pendente para exercitar o atraso.
 */
@Component
@RequiredArgsConstructor
public class ExpenseSeeder implements Seeder {

    private final ExpenseRepository expenseRepository;
    private final Clock clock;

    @Override
    public int order() {
        return 6;
    }

    @Override
    public void seed(SeedContext context) {
        if (expenseRepository.existsByUserId(context.getUser().getId())) {
            return;
        }

        var today = LocalDate.now(clock);
        var month = YearMonth.from(today);
        var nubankDue = month.atDay(15);
        var mercadoPagoDue = month.atDay(14);

        var expenses = new ArrayList<Expense>();

        // FIXED / PAID — em cartão, assinatura
        expenses.add(Expense.builder()
            .description("iCloud")
            .value(new BigDecimal("19.90"))
            .dueDate(nubankDue)
            .expenseType(ExpenseType.FIXED)
            .paymentStatus(PaymentStatus.PAID)
            .user(context.getUser())
            .cardAccount(context.card("Nubank"))
            .category(context.category("Assinaturas"))
            .build());

        // VARIABLE / PAID — em cartão, alimentação
        expenses.add(Expense.builder()
            .description("Mercado")
            .value(new BigDecimal("350.00"))
            .dueDate(nubankDue)
            .expenseType(ExpenseType.VARIABLE)
            .paymentStatus(PaymentStatus.PAID)
            .user(context.getUser())
            .cardAccount(context.card("Nubank"))
            .category(context.category("Alimentação"))
            .build());

        // PARCELED / PAID — parcela final (5/5) de uma compra que já terminou, lazer
        expenses.addAll(installmentsOf(Expense.builder()
            .description("Compra parcelada")
            .value(new BigDecimal("100.76"))
            .dueDate(nubankDue)
            .expenseType(ExpenseType.PARCELED)
            .paymentStatus(PaymentStatus.PAID)
            .currentInstallment(5)
            .totalInstallment(5)
            .user(context.getUser())
            .cardAccount(context.card("Nubank"))
            .category(context.category("Lazer"))
            .build()));

        // PARCELED / PENDING — compra em curso (4/10): as parcelas 4..10 existem, uma por mês, no mesmo grupo
        expenses.addAll(installmentsOf(Expense.builder()
            .description("Eletrônico")
            .value(new BigDecimal("139.00"))
            .dueDate(mercadoPagoDue)
            .expenseType(ExpenseType.PARCELED)
            .paymentStatus(PaymentStatus.PENDING)
            .currentInstallment(4)
            .totalInstallment(10)
            .user(context.getUser())
            .cardAccount(context.card("Mercado Pago"))
            .category(context.category("Lazer"))
            .build()));

        // VARIABLE / PAID — saúde, paga ontem (com a data real do pagamento)
        expenses.add(Expense.builder()
            .description("Farmácia")
            .value(new BigDecimal("45.00"))
            .dueDate(mercadoPagoDue)
            .expenseType(ExpenseType.VARIABLE)
            .paymentStatus(PaymentStatus.PAID)
            .paidAt(today.minusDays(1))
            .user(context.getUser())
            .cardAccount(context.card("Mercado Pago"))
            .category(context.category("Saúde"))
            .build());

        // FIXED / PENDING — avulsa (sem cartão), transporte
        expenses.add(Expense.builder()
            .description("Busão")
            .value(new BigDecimal("80.32"))
            .dueDate(month.atDay(25))
            .expenseType(ExpenseType.FIXED)
            .paymentStatus(PaymentStatus.PENDING)
            .user(context.getUser())
            .category(context.category("Transporte"))
            .build());

        // VARIABLE / PENDING vencida — avulsa, moradia: sempre em atraso (venceu há 3 dias e ninguém marcou como paga)
        expenses.add(Expense.builder()
            .description("Conta de luz")
            .value(new BigDecimal("132.40"))
            .dueDate(today.minusDays(3))
            .expenseType(ExpenseType.VARIABLE)
            .paymentStatus(PaymentStatus.PENDING)
            .user(context.getUser())
            .category(context.category("Moradia"))
            .build());

        expenseRepository.saveAll(expenses);
    }

    // As linhas de uma compra parcelada: a informada e as seguintes, todas com o mesmo grupo.
    private List<Expense> installmentsOf(Expense first) {
        first.setInstallmentGroupId(UUID.randomUUID());
        return InstallmentSchedule.from(first).installments();
    }

}
