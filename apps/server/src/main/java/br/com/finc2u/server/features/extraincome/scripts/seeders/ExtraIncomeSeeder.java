package br.com.finc2u.server.features.extraincome.scripts.seeders;

import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import br.com.finc2u.server.features.extraincome.repository.ExtraIncomeRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.shared.seed.SeedContext;
import br.com.finc2u.server.shared.seed.Seeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ExtraIncomeSeeder implements Seeder {

    private final ExtraIncomeRepository extraIncomeRepository;
    private final Clock clock;

    @Override
    public int order() {
        return 5;
    }

    @Override
    public void seed(SeedContext context) {
        if (extraIncomeRepository.existsByUserId(context.get(User.class).getId())) return;

        // Relativo ao mês corrente (do Clock): o resumo de hoje sempre tem ganhos extras.
        var competencia = YearMonth.now(clock).atDay(15);
        var extraIncomes = List.of(
            ExtraIncome.builder()
                .description("Mãe")
                .value(new BigDecimal("4470.00"))
                .date(competencia)
                .user(context.get(User.class))
                .build(),
            ExtraIncome.builder()
                .description("Tio Luiz")
                .value(new BigDecimal("305.50"))
                .date(competencia)
                .user(context.get(User.class))
                .build()
        );

        extraIncomeRepository.saveAll(extraIncomes);
    }

}
