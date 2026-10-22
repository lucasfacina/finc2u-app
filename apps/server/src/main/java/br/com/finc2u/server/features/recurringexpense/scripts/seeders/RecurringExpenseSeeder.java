package br.com.finc2u.server.features.recurringexpense.scripts.seeders;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.materializer.RecurringExpenseMaterializer;
import br.com.finc2u.server.features.recurringexpense.repository.RecurringExpenseRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.shared.seed.SeedContext;
import br.com.finc2u.server.shared.seed.Seeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Stream;

/*
 * Modelos de despesa recorrente de dev, com o mês corrente e o seguinte já gerados. Roda depois do ExpenseSeeder (a
 * guarda dele é "o usuário já tem despesa", e a geração abaixo cria despesas). É idempotente por descrição, e gerar de
 * novo não duplica: subir o app num mês novo completa as despesas do mês, mesmo num banco já populado.
 */
@Component
@RequiredArgsConstructor
public class RecurringExpenseSeeder implements Seeder {

    private final RecurringExpenseRepository recurringExpenseRepository;
    private final RecurringExpenseMaterializer recurringExpenseMaterializer;
    private final Clock clock;

    @Override
    public int order() {
        return 7;
    }

    @Override
    public void seed(SeedContext context) {
        var existing = recurringExpenseRepository.findByUserIdOrderByDescriptionAsc(context.get(User.class).getId());
        var existingDescriptions = existing.stream()
            .map(RecurringExpense::getDescription)
            .toList();

        var missing = seedModels(context).stream()
            .filter(model -> !existingDescriptions.contains(model.getDescription()))
            .toList();
        recurringExpenseRepository.saveAll(missing);

        Stream.concat(existing.stream(), missing.stream())
            .forEach(recurringExpenseMaterializer::materializeUpcoming);
    }

    private List<RecurringExpense> seedModels(SeedContext context) {
        var currentMonth = YearMonth.now(clock);

        // Cobrada dia 5, antes do fechamento do cartão (dia 9): cai na fatura que vence no próprio mês (dia 15).
        var netflix = RecurringExpense.of("Netflix", new BigDecimal("20.90"), 5, currentMonth, null);
        netflix.setCategory(context.get(Category.class, "Assinaturas"));
        netflix.setCardAccount(context.get(CardAccount.class, "Nubank"));

        var vivo = RecurringExpense.of("Vivo", new BigDecimal("39.00"), 17, currentMonth, null);
        vivo.setCategory(context.get(Category.class, "Assinaturas"));

        // Com mês de término: gera até lá e para.
        var gympass = RecurringExpense.of("Gympass", new BigDecimal("99.90"), 5, currentMonth, currentMonth.plusMonths(5));
        gympass.setCategory(context.get(Category.class, "Saúde"));
        gympass.setCardAccount(context.get(CardAccount.class, "Mercado Pago"));

        var models = List.of(netflix, vivo, gympass);
        models.forEach(model -> model.setUser(context.get(User.class)));
        return models;
    }

}
