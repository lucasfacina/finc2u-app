package br.com.finc2u.server.features.expense.integration;

import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.user.entity.User;
import br.com.finc2u.server.features.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * A categoria informada por nome, de ponta a ponta (HTTP até o banco): a que não existe é criada no mesmo gesto do lançamento, a que existe
 * é reaproveitada sem diferenciar maiúsculas de minúsculas, e cada usuário tem as suas.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CategoryByNameIntegrationTest {

    // Mês no futuro: o status padrão da despesa é PENDING e não exige informar o pagamento.
    private static final String DUE_DATE = "2027-03-10";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryService categoryService;

    @Test
    void postExpense_shouldCreateTheCategoryOnTheFly_whenTheNameDoesNotExistYet() throws Exception {
        var userId = registerUser("categoria.nome.cria@email.com");

        postExpenseByName(userId, "Pets")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.category.name").value("Pets"));

        assertEquals(9, categoryService.getByUser(userId).size());
        assertTrue(namesOf(userId).contains("Pets"));
    }

    @Test
    void postExpense_shouldReuseTheSameCategory_ignoringCase_andNotCreateAnotherOne() throws Exception {
        var userId = registerUser("categoria.nome.reusa@email.com");
        postExpenseByName(userId, "Pets").andExpect(status().isCreated());
        var petsId = petsIdOf(userId);

        postExpenseByName(userId, "  pETS ")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.category.id").value(petsId.toString()))
            .andExpect(jsonPath("$.category.name").value("Pets"));

        assertEquals(9, categoryService.getByUser(userId).size());
    }

    // "lazer" já existe entre as padrão ("Lazer"): usa a existente, sem criar outra.
    @Test
    void postExpense_shouldUseADefaultCategory_whenTheNameMatchesIt() throws Exception {
        var userId = registerUser("categoria.nome.padrao@email.com");

        postExpenseByName(userId, "lazer")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.category.name").value("Lazer"));

        assertEquals(8, categoryService.getByUser(userId).size());
    }

    @Test
    void postExpense_shouldKeepEachUsersCategoriesApart() throws Exception {
        var ana = registerUser("categoria.nome.ana@email.com");
        var beto = registerUser("categoria.nome.beto@email.com");

        postExpenseByName(ana, "Pets").andExpect(status().isCreated());
        postExpenseByName(beto, "Pets").andExpect(status().isCreated());

        assertEquals(9, categoryService.getByUser(ana).size());
        assertEquals(9, categoryService.getByUser(beto).size());
        assertNotEquals(petsIdOf(ana), petsIdOf(beto));
    }

    @Test
    void postExpense_shouldCreateNothing_whenTheCategoryComesByIdAndByName() throws Exception {
        var userId = registerUser("categoria.nome.dupla@email.com");
        var anyCategoryId = categoryService.getByUser(userId).getFirst().getId();

        mockMvc.perform(post("/expenses").param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(expenseJson("\"categoryId\": \"%s\", \"categoryName\": \"Pets\"".formatted(anyCategoryId))))
            .andExpect(status().isBadRequest());

        assertEquals(8, categoryService.getByUser(userId).size());
    }

    @Test
    void postRecurringExpense_shouldCreateTheCategoryOnTheFly_andShareItWithTheExpenses() throws Exception {
        var userId = registerUser("categoria.nome.recorrente@email.com");

        mockMvc.perform(post("/recurring-expenses").param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"description": "Ração mensal", "value": 80, "dayOfMonth": 10, "startMonth": "2027-03",
                     "categoryName": "Pets"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.category.name").value("Pets"));
        postExpenseByName(userId, "pets").andExpect(status().isCreated());

        assertEquals(9, categoryService.getByUser(userId).size());
    }

    private ResultActions postExpenseByName(UUID userId, String categoryName) throws Exception {
        return mockMvc.perform(post("/expenses").param("userId", userId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(expenseJson("\"categoryName\": \"%s\"".formatted(categoryName))));
    }

    private String expenseJson(String categoryFields) {
        return """
            {"description": "Compra", "value": 80, "expenseType": "VARIABLE", "dueDate": "%s", %s}
            """.formatted(DUE_DATE, categoryFields);
    }

    private List<String> namesOf(UUID userId) {
        return categoryService.getByUser(userId).stream().map(Category::getName).toList();
    }

    private UUID petsIdOf(UUID userId) {
        return categoryService.getByUser(userId).stream()
            .filter(category -> "Pets".equals(category.getName()))
            .map(Category::getId)
            .findFirst()
            .orElseThrow();
    }

    private UUID registerUser(String email) {
        var user = User.builder()
            .name("Ana")
            .email(email)
            .savingsBalance(BigDecimal.valueOf(100))
            .build();
        return userService.create(user, BigDecimal.valueOf(3000), LocalDate.of(2026, 1, 1)).getId();
    }

}
