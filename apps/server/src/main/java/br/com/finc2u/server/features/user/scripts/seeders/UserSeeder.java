package br.com.finc2u.server.features.user.scripts.seeders;

import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.repository.UserRepository;
import br.com.finc2u.server.features.user.service.UserService;
import br.com.finc2u.server.shared.seed.SeedContext;
import br.com.finc2u.server.shared.seed.Seeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class UserSeeder implements Seeder {

    private static final String SEED_EMAIL = "facina3@gmail.com";

    private final UserRepository userRepository;
    private final UserService userService;

    @Override
    public int order() {
        return 1;
    }

    @Override
    public void seed(SeedContext context) {
        context.setUser(userRepository.findByEmail(SEED_EMAIL)
            .orElseGet(this::registerSeedUser));
    }

    // Cadastra pelo caminho de um cadastro real: o primeiro salário e as categorias padrão nascem dos listeners do
    // UserRegisteredEvent, e o seed não precisa conhecer a feature do salário.
    private User registerSeedUser() {
        var user = User.builder()
            .name("Lucas Facina")
            .email(SEED_EMAIL)
            .savingsBalance(new BigDecimal("5000.00"))
            .build();

        return userService.create(user, new BigDecimal("3000.00"), LocalDate.of(2026, 1, 1));
    }

}
