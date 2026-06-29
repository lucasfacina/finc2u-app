package br.com.finc2u.server.features.expense.scripts.seeders;


import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.repository.ExpenseRepository;
import br.com.finc2u.server.shared.seed.SeedContext;
import br.com.finc2u.server.shared.seed.Seeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ExpenseSeeder implements Seeder {

    private final ExpenseRepository expenseRepository;

    private static final LocalDate NUBANK_DUE = LocalDate.of(2026, 6, 15);
    private static final LocalDate MELI_DUE = LocalDate.of(2026, 6, 14);

    @Override
    public int order() {
        return 5;
    }

    @Override
    public void seed(SeedContext context) {
        List<Expense> expenses = List.of(
                // FIXED / PAID — em cartão, assinaturas
                Expense.builder()
                        .description("Netflix")
                        .value(new BigDecimal("20.90"))
                        .dueDate(NUBANK_DUE)
                        .expenseType(ExpenseType.FIXED)
                        .paymentStatus(PaymentStatus.PAID)
                        .user(context.getUser())
                        .cardAccount(context.card("Nubank"))
                        .tags(List.of(context.tag("Assinaturas")))
                        .build(),
                Expense.builder()
                        .description("iCloud")
                        .value(new BigDecimal("19.90"))
                        .dueDate(NUBANK_DUE)
                        .expenseType(ExpenseType.FIXED)
                        .paymentStatus(PaymentStatus.PAID)
                        .user(context.getUser())
                        .cardAccount(context.card("Nubank"))
                        .tags(List.of(context.tag("Assinaturas")))
                        .build(),

                // VARIABLE / PAID — em cartão, alimentação
                Expense.builder()
                        .description("Mercado")
                        .value(new BigDecimal("350.00"))
                        .dueDate(NUBANK_DUE)
                        .expenseType(ExpenseType.VARIABLE)
                        .paymentStatus(PaymentStatus.PAID)
                        .user(context.getUser())
                        .cardAccount(context.card("Nubank"))
                        .tags(List.of(context.tag("Alimentação")))
                        .build(),

                // PARCELED / PAID — parcela final (5/5), lazer
                Expense.builder()
                        .description("Compra parcelada")
                        .value(new BigDecimal("100.76"))
                        .dueDate(NUBANK_DUE)
                        .expenseType(ExpenseType.PARCELED)
                        .paymentStatus(PaymentStatus.PAID)
                        .currentInstallment(5)
                        .totalInstallment(5)
                        .user(context.getUser())
                        .cardAccount(context.card("Nubank"))
                        .tags(List.of(context.tag("Lazer")))
                        .build(),

                // PARCELED / PENDING — parcela em curso (4/10), outro cartão, lazer
                Expense.builder()
                        .description("Eletrônico")
                        .value(new BigDecimal("139.00"))
                        .dueDate(MELI_DUE)
                        .expenseType(ExpenseType.PARCELED)
                        .paymentStatus(PaymentStatus.PENDING)
                        .currentInstallment(4).totalInstallment(10)
                        .user(context.getUser())
                        .cardAccount(context.card("Mercado Pago"))
                        .tags(List.of(context.tag("Lazer")))
                        .build(),

                // VARIABLE / PAID — saúde
                Expense.builder()
                        .description("Farmácia")
                        .value(new BigDecimal("45.00"))
                        .dueDate(MELI_DUE)
                        .expenseType(ExpenseType.VARIABLE)
                        .paymentStatus(PaymentStatus.PAID)
                        .user(context.getUser())
                        .cardAccount(context.card("Mercado Pago"))
                        .tags(List.of(context.tag("Saúde")))
                        .build(),

                // FIXED / PAID — avulsa (sem cartão), assinaturas
                Expense.builder()
                        .description("Vivo")
                        .value(new BigDecimal("39.00"))
                        .dueDate(LocalDate.of(2026, 6, 17))
                        .expenseType(ExpenseType.FIXED)
                        .paymentStatus(PaymentStatus.PAID)
                        .user(context.getUser())
                        .tags(List.of(context.tag("Assinaturas")))
                        .build(),

                // FIXED / PENDING — avulsa (sem cartão), transporte
                Expense.builder()
                        .description("Busão")
                        .value(new BigDecimal("80.32"))
                        .dueDate(LocalDate.of(2026, 6, 25))
                        .expenseType(ExpenseType.FIXED)
                        .paymentStatus(PaymentStatus.PENDING)
                        .user(context.getUser())
                        .tags(List.of(context.tag("Transporte")))
                        .build()
        );

        if (expenseRepository.existsByUserId(context.getUser().getId())) {
            return;
        }

        expenseRepository.saveAll(expenses);
    }

}
