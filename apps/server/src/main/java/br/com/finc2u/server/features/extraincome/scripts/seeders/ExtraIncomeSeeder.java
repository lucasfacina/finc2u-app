package br.com.finc2u.server.features.extraincome.scripts.seeders;

import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import br.com.finc2u.server.features.extraincome.repository.ExtraIncomeRepository;
import br.com.finc2u.server.shared.seed.SeedContext;
import br.com.finc2u.server.shared.seed.Seeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ExtraIncomeSeeder implements Seeder {

    private final ExtraIncomeRepository extraIncomeRepository;

    private static final LocalDate COMPETENCIA = LocalDate.of(2026, 6, 15);

    @Override
    public int order() {
        return 5;
    }

    @Override
    public void seed(SeedContext context) {
        List<ExtraIncome> extraIncomes = List.of(
            ExtraIncome.builder()
                .description("Mãe")
                .value(new BigDecimal("4470.00"))
                .date(COMPETENCIA)
                .user(context.getUser())
                .build(),
            ExtraIncome.builder()
                .description("Tio Luiz")
                .value(new BigDecimal("305.50"))
                .date(COMPETENCIA)
                .user(context.getUser())
                .build()
        );

        if (extraIncomeRepository.existsByUserId(context.getUser().getId())) {
            return;
        }

        extraIncomeRepository.saveAll(extraIncomes);
    }

}
