package br.com.finc2u.server.features.tag.scripts.seeders;

import br.com.finc2u.server.features.tag.entity.Tag;
import br.com.finc2u.server.features.tag.repository.TagRepository;
import br.com.finc2u.server.shared.seed.SeedContext;
import br.com.finc2u.server.shared.seed.Seeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TagSeeder implements Seeder {

    private final TagRepository tagRepository;

    @Override
    public int order() {
        return 3;
    }

    @Override
    public void seed(SeedContext context) {
        var user = context.getUser();
        var names = seedTagNames();

        // Encontrar ou criar por item: tag ausente é criada mesmo que as demais já existam (context.tag nunca devolve null).
        var existingTags = tagRepository.findByUserIdAndNameIn(user.getId(), names);
        existingTags.forEach(context::putTag);

        var existingNames = existingTags.stream().map(Tag::getName).toList();
        var missingTags = names.stream()
            .filter(name -> !existingNames.contains(name))
            .map(name -> {
                var tag = Tag.of(name);
                tag.setUser(user);
                return tag;
            })
            .toList();

        tagRepository.saveAll(missingTags).forEach(context::putTag);
    }

    private List<String> seedTagNames() {
        return List.of("Essencial", "Supérfluo", "Reembolsável", "Dividido", "Recorrente");
    }

}
