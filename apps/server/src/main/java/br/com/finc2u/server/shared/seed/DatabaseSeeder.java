package br.com.finc2u.server.shared.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seeders.enabled", havingValue = "true")
public class DatabaseSeeder implements CommandLineRunner {

    private final List<Seeder> seeders;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("🚀 Iniciando processo de Database Seeding...");
        SeedContext context = new SeedContext();
        seeders.stream()
                .sorted(Comparator.comparingInt(Seeder::order))
                .forEach(seeder -> seeder.seed(context));
        log.info("✅ Processo de Database Seeding finalizado com sucesso!");
    }

}
