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
        List<Tag> seedTags = createSeedTags();
        List<String> names = seedTags.stream().map(Tag::getName).toList();

        List<Tag> existingTags = tagRepository.findByNameIn(names);
        if (!existingTags.isEmpty()) {
            existingTags.forEach(context::putTag);
            return;
        }

        tagRepository.saveAll(seedTags).forEach(context::putTag);
    }

    private List<Tag> createSeedTags() {
        return List.of(
                Tag.builder()
                        .name("Alimentação")
                        .colorCode(4280391411L)
                        .build(),
                Tag.builder().
                        name("Transporte")
                        .colorCode(4278190335L)
                        .build(),
                Tag.builder()
                        .name("Assinaturas")
                        .colorCode(4287365120L)
                        .build(),
                Tag.builder()
                        .name("Saúde")
                        .colorCode(4278255360L)
                        .build(),
                Tag.builder()
                        .name("Lazer")
                        .colorCode(4294902015L)
                        .build()
        );
    }

}