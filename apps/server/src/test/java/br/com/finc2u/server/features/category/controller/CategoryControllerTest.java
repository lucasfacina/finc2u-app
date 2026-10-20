package br.com.finc2u.server.features.category.controller;

import br.com.finc2u.server.config.GlobalExceptionHandler;
import br.com.finc2u.server.exception.BusinessException;
import br.com.finc2u.server.features.category.dto.CategoryRequest;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.category.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CategoryController.class, GlobalExceptionHandler.class})
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @Autowired
    private ObjectMapper objectMapper;

    private Category category;
    private UUID categoryId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();
        userId = UUID.randomUUID();
        category = Category.builder()
            .name("Alimentação")
            .build();
        category.setId(categoryId);
    }

    @Test
    void create_shouldReturnCreated() throws Exception {
        var request = new CategoryRequest("Alimentação");
        when(categoryService.create(any(Category.class), eq(userId)))
            .thenReturn(category);

        mockMvc.perform(post("/categories")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(categoryId.toString()))
            .andExpect(jsonPath("$.name").value("Alimentação"));
    }

    @Test
    void create_shouldReturnBadRequest_whenNameIsBlank() throws Exception {
        var invalidRequest = new CategoryRequest("");

        mockMvc.perform(post("/categories")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_shouldReturnBadRequest_whenNameIsLongerThanSixtyCharacters() throws Exception {
        var invalidRequest = new CategoryRequest("a".repeat(61));

        mockMvc.perform(post("/categories")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_shouldReturnBadRequest_whenUserIdIsMissing() throws Exception {
        var request = new CategoryRequest("Alimentação");

        mockMvc.perform(post("/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_shouldReturnUnprocessableEntity_whenNameAlreadyExists() throws Exception {
        var request = new CategoryRequest("Alimentação");
        when(categoryService.create(any(Category.class), eq(userId)))
            .thenThrow(new BusinessException("Categoria já cadastrada!"));

        mockMvc.perform(post("/categories")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void getByUserId_shouldReturnOk() throws Exception {
        when(categoryService.getByUser(userId))
            .thenReturn(List.of(category));

        mockMvc.perform(get("/categories")
                .param("userId", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Alimentação"));
    }

    @Test
    void getByUserId_shouldReturnBadRequest_whenUserIdIsMissing() throws Exception {
        mockMvc.perform(get("/categories"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getById_shouldReturnOk() throws Exception {
        when(categoryService.getById(categoryId))
            .thenReturn(category);

        mockMvc.perform(get("/categories/{id}", categoryId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(categoryId.toString()));
    }

    @Test
    void update_shouldReturnOk() throws Exception {
        var request = new CategoryRequest("Alimentação");
        when(categoryService.update(eq(categoryId), any(Category.class)))
            .thenReturn(category);

        mockMvc.perform(put("/categories/{id}", categoryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Alimentação"));
    }

    @Test
    void delete_shouldReturnNoContent() throws Exception {
        doNothing().when(categoryService).delete(categoryId);

        mockMvc.perform(delete("/categories/{id}", categoryId))
            .andExpect(status().isNoContent());
    }

    @Test
    void delete_shouldReturnConflict_whenCategoryHasExpenses() throws Exception {
        doThrow(new DataIntegrityViolationException("FK de despesas"))
            .when(categoryService).delete(categoryId);

        mockMvc.perform(delete("/categories/{id}", categoryId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

}
