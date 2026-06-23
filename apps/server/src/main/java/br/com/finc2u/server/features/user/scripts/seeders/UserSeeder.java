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

    private final UserRepository userRepository;

    @Override
    public int order() {
        return 1;
    }

    @Override
    public void seed(SeedContext context) {
        UserConfiguration configuration = new UserConfiguration(
                new BigDecimal("3000.00"),
                new BigDecimal("5000.00")
        );

        User user = User.builder()
                .name("Lucas Facina")
                .email("facina3@gmail.com")
                .userConfiguration(configuration)
                .build();

        context.setUser(userRepository.save(user));
    }

}
