package br.com.finc2u.server.features.extra.scripts.seeders;

import br.com.finc2u.server.features.extra.entity.Extra;
import br.com.finc2u.server.features.extra.repository.ExtraRepository;
import br.com.finc2u.server.shared.seed.SeedContext;
import br.com.finc2u.server.shared.seed.Seeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ExtraSeeder implements Seeder {

    private final ExtraRepository extraRepository;

    private static final LocalDate COMPETENCIA = LocalDate.of(2026, 6, 15);

    @Override
    public int order() {
        return 4;
    }

    @Override
    public void seed(SeedContext context) {
        List<Extra> extras = List.of(
                Extra.builder()
                        .description("Mãe")
                        .value(new BigDecimal("4470.00"))
                        .date(COMPETENCIA)
                        .user(context.getUser())
                        .build(),
                Extra.builder()
                        .description("Tio Luiz")
                        .value(new BigDecimal("305.50"))
                        .date(COMPETENCIA)
                        .user(context.getUser())
                        .build()
        );

        if (extraRepository.existsByUserId(context.getUser().getId())) {
            return;
        }

        extraRepository.saveAll(extras);
    }

}
