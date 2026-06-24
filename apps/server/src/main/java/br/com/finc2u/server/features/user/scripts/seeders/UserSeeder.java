package br.com.finc2u.server.features.user.scripts.seeders;

import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.entity.UserConfiguration;
import br.com.finc2u.server.features.user.repository.UserRepository;
import br.com.finc2u.server.shared.seed.SeedContext;
import br.com.finc2u.server.shared.seed.Seeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class UserSeeder implements Seeder {

    private static final String SEED_EMAIL = "facina3@gmail.com";

    private final UserRepository userRepository;

    @Override
    public int order() {
        return 1;
    }

    @Override
    public void seed(SeedContext context) {
        User user = userRepository.findByEmail(SEED_EMAIL)
                .orElseGet(this::createSeedUser);

        context.setUser(user);
    }

    private User createSeedUser() {
        UserConfiguration configuration = new UserConfiguration(
                new BigDecimal("3000.00"),
                new BigDecimal("5000.00")
        );

        User user = User.builder()
                .name("Lucas Facina")
                .email(SEED_EMAIL)
                .userConfiguration(configuration)
                .build();

        return userRepository.save(user);
    }

}
