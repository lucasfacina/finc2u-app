package br.com.finc2u.server.features.category.service;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.category.enums.DefaultCategory;
import br.com.finc2u.server.features.category.repository.CategoryRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserService userService;

    @Transactional
    public Category create(Category category, UUID userId) {
        var user = userService.getById(userId);

        // A constraint unique do banco diferencia maiúsculas de minúsculas ("Lazer" × "lazer"); esta checagem não,
        // e devolve um erro de negócio claro em vez de uma violação de integridade.
        if (categoryRepository.findByUserIdAndNameIgnoreCase(userId, category.getName()).isPresent())
            throw new BusinessException("Categoria já cadastrada!");

        category.setUser(user);

        return categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public Category getById(UUID id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada com o ID: " + id));
    }

    // Categoria de outro usuário é tratada como inexistente: a resposta não revela que o id existe.
    @Transactional(readOnly = true)
    public Category getByIdAndUser(UUID id, UUID userId) {
        return categoryRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada com o ID: " + id));
    }

    /*
     * A categoria do usuário pelo nome (sem diferenciar maiúsculas de minúsculas); se ainda não existe, é criada na hora. Serve ao
     * lançamento que informa a categoria por nome: cada pessoa tem as suas, e a que falta nasce no mesmo gesto.
     */
    @Transactional
    public Category getOrCreateByName(UUID userId, String name) {
        var trimmed = name.trim();
        return categoryRepository.findByUserIdAndNameIgnoreCase(userId, trimmed)
            .orElseGet(() -> create(Category.of(trimmed), userId));
    }

    @Transactional(readOnly = true)
    public List<Category> getByUser(UUID userId) {
        userService.getById(userId);
        return categoryRepository.findByUserIdOrderByNameAsc(userId);
    }

    /*
     * Cria as categorias padrão que o usuário ainda não tem (nome comparado sem diferenciar maiúsculas de
     * minúsculas), então pode ser chamado de novo sem duplicar nada.
     */
    @Transactional
    public void createDefaults(User user) {
        var existingNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        categoryRepository.findByUserIdOrderByNameAsc(user.getId())
            .forEach(category -> existingNames.add(category.getName()));

        var missingCategories = Arrays.stream(DefaultCategory.values())
            .map(DefaultCategory::getCategoryName)
            .filter(name -> !existingNames.contains(name))
            .map(name -> {
                var category = Category.of(name);
                category.setUser(user);
                return category;
            })
            .toList();

        categoryRepository.saveAll(missingCategories);
    }

    @Transactional
    public Category update(UUID id, Category categoryUpdated) {
        var category = getById(id);

        categoryRepository.findByUserIdAndNameIgnoreCase(category.getUser().getId(), categoryUpdated.getName())
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(__ -> {
                throw new BusinessException("Categoria já cadastrada!");
            });

        category.setName(categoryUpdated.getName());

        return categoryRepository.save(category);
    }

    @Transactional
    public void delete(UUID id) {
        var category = getById(id);
        categoryRepository.delete(category);
    }

}
