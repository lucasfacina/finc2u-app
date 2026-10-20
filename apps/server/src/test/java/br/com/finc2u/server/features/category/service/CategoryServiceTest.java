package br.com.finc2u.server.features.category.service;

import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.category.repository.CategoryRepository;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private CategoryService categoryService;

    private User user;
    private UUID userId;
    private Category category;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
            .name("Ana")
            .email("ana@email.com")
            .build();
        user.setId(userId);

        categoryId = UUID.randomUUID();
        category = Category.builder()
            .name("Alimentação")
            .user(user)
            .build();
        category.setId(categoryId);
    }

    @Test
    void create_shouldAttachUserAndSave_whenNameIsUniqueForTheUser() {
        var newCategory = Category.of("Alimentação");
        when(userService.getById(userId))
            .thenReturn(user);
        when(categoryRepository.findByUserIdAndNameIgnoreCase(userId, "Alimentação"))
            .thenReturn(Optional.empty());
        when(categoryRepository.save(newCategory))
            .thenReturn(category);

        var response = categoryService.create(newCategory, userId);

        assertSame(category, response);
        assertSame(user, newCategory.getUser());
        verify(categoryRepository, times(1)).save(newCategory);
    }

    // "Mesmo nome em outro usuário é aceito" é garantido pela consulta escopada por usuário (CategoryRepositoryTest).
    @Test
    void create_shouldThrowBusinessException_whenNameAlreadyExistsForTheUser_ignoringCase() {
        var newCategory = Category.of("ALIMENTAÇÃO");
        when(userService.getById(userId))
            .thenReturn(user);
        when(categoryRepository.findByUserIdAndNameIgnoreCase(userId, "ALIMENTAÇÃO"))
            .thenReturn(Optional.of(category));

        assertThrows(BusinessException.class, () -> categoryService.create(newCategory, userId));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void create_shouldThrowResourceNotFoundException_whenUserDoesNotExist() {
        when(userService.getById(userId))
            .thenThrow(new ResourceNotFoundException("Usuário não encontrado"));

        assertThrows(ResourceNotFoundException.class, () -> categoryService.create(Category.of("Alimentação"), userId));
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void getById_shouldReturnCategory_whenIdExists() {
        when(categoryRepository.findById(categoryId))
            .thenReturn(Optional.of(category));

        var response = categoryService.getById(categoryId);

        assertEquals(categoryId, response.getId());
    }

    @Test
    void getById_shouldThrowResourceNotFoundException_whenIdDoesNotExist() {
        when(categoryRepository.findById(categoryId))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.getById(categoryId));
    }

    @Test
    void getByIdAndUser_shouldReturnCategory_whenItBelongsToTheUser() {
        when(categoryRepository.findByIdAndUserId(categoryId, userId))
            .thenReturn(Optional.of(category));

        var response = categoryService.getByIdAndUser(categoryId, userId);

        assertSame(category, response);
    }

    // Categoria de outro usuário não volta da consulta escopada: o erro é o mesmo de id inexistente.
    @Test
    void getByIdAndUser_shouldThrowResourceNotFoundException_whenCategoryBelongsToAnotherUser() {
        when(categoryRepository.findByIdAndUserId(categoryId, userId))
            .thenReturn(Optional.empty());

        var exception = assertThrows(ResourceNotFoundException.class,
            () -> categoryService.getByIdAndUser(categoryId, userId));

        assertEquals("Categoria não encontrada com o ID: " + categoryId, exception.getMessage());
    }

    @Test
    void getByUser_shouldReturnCategoriesOfTheUser() {
        when(userService.getById(userId))
            .thenReturn(user);
        when(categoryRepository.findByUserIdOrderByNameAsc(userId))
            .thenReturn(List.of(category));

        var response = categoryService.getByUser(userId);

        assertEquals(List.of(category), response);
    }

    @Test
    void getByUser_shouldThrowResourceNotFoundException_whenUserDoesNotExist() {
        when(userService.getById(userId))
            .thenThrow(new ResourceNotFoundException("Usuário não encontrado"));

        assertThrows(ResourceNotFoundException.class, () -> categoryService.getByUser(userId));
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void update_shouldReturnUpdatedCategory_whenCategoryExists() {
        var categoryUpdates = Category.of("Mercado");
        when(categoryRepository.findById(categoryId))
            .thenReturn(Optional.of(category));
        when(categoryRepository.findByUserIdAndNameIgnoreCase(userId, "Mercado"))
            .thenReturn(Optional.empty());
        when(categoryRepository.save(category))
            .thenReturn(category);

        var response = categoryService.update(categoryId, categoryUpdates);

        assertNotNull(response);
        assertEquals("Mercado", category.getName());
        verify(categoryRepository, times(1)).save(category);
    }

    @Test
    void update_shouldThrowBusinessException_whenNameAlreadyExistsOnAnotherCategoryOfTheUser() {
        var other = Category.builder()
            .name("Mercado")
            .user(user)
            .build();
        other.setId(UUID.randomUUID());
        when(categoryRepository.findById(categoryId))
            .thenReturn(Optional.of(category));
        when(categoryRepository.findByUserIdAndNameIgnoreCase(userId, "mercado"))
            .thenReturn(Optional.of(other));

        assertThrows(BusinessException.class, () -> categoryService.update(categoryId, Category.of("mercado")));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void update_shouldAllowSameName_whenRenameToOwnCurrentName() {
        when(categoryRepository.findById(categoryId))
            .thenReturn(Optional.of(category));
        when(categoryRepository.findByUserIdAndNameIgnoreCase(userId, "Alimentação"))
            .thenReturn(Optional.of(category));
        when(categoryRepository.save(category))
            .thenReturn(category);

        assertDoesNotThrow(() -> categoryService.update(categoryId, Category.of("Alimentação")));
        verify(categoryRepository, times(1)).save(category);
    }

    @Test
    void delete_shouldCallDelete_whenCategoryExists() {
        when(categoryRepository.findById(categoryId))
            .thenReturn(Optional.of(category));

        categoryService.delete(categoryId);

        verify(categoryRepository, times(1)).delete(category);
    }

    @Test
    void delete_shouldThrowResourceNotFoundException_whenCategoryDoesNotExist() {
        when(categoryRepository.findById(categoryId))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.delete(categoryId));
        verify(categoryRepository, never()).delete(any(Category.class));
    }

}
