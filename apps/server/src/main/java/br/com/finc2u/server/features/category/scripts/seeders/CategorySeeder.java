package br.com.finc2u.server.features.category.scripts.seeders;

import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.category.enums.DefaultCategory;
import br.com.finc2u.server.features.category.repository.CategoryRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.shared.seed.SeedContext;
import br.com.finc2u.server.shared.seed.Seeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class CategorySeeder implements Seeder {

    private final CategoryRepository categoryRepository;

    @Override
    public int order() {
        return 4;
    }

    @Override
    public void seed(SeedContext context) {
        var user = context.get(User.class);

        var existingCategories = categoryRepository.findByUserIdOrderByNameAsc(user.getId());
        existingCategories.forEach(category -> remember(context, category));

        var existingNames = existingCategories.stream().map(Category::getName).toList();
        var missingCategories = Arrays.stream(DefaultCategory.values())
            .map(DefaultCategory::getCategoryName)
            .filter(name -> !existingNames.contains(name))
            .map(name -> {
                var category = Category.of(name);
                category.setUser(user);
                return category;
            })
            .toList();

        categoryRepository.saveAll(missingCategories).forEach(category -> remember(context, category));
    }

    private void remember(SeedContext context, Category category) {
        context.put(Category.class, category.getName(), category);
    }

}
