package br.com.finc2u.server.features.recurringexpense.mapper;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.card.service.CardAccountService;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import br.com.finc2u.server.features.recurringexpense.vo.RecurringExpenseCommand;
import br.com.finc2u.server.features.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringExpenseMapperTest {

    @Mock
    private CategoryService categoryService;

    @Mock
    private CardAccountService cardAccountService;

    @InjectMocks
    private RecurringExpenseMapper recurringExpenseMapper;

    private User owner;
    private UUID ownerId;
    private Category category;
    private UUID categoryId;
    private CardAccount card;
    private UUID cardId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        owner = User.builder()
            .name("Ana")
            .build();
        owner.setId(ownerId);

        categoryId = UUID.randomUUID();
        category = Category.builder()
            .name("Assinaturas")
            .build();
        category.setId(categoryId);

        cardId = UUID.randomUUID();
        card = CardAccount.builder()
            .cardName("Nubank")
            .build();
        card.setId(cardId);
    }

    private RecurringExpense modelOf(String description) {
        var model = RecurringExpense.of(description, BigDecimal.valueOf(21.90), 5, YearMonth.of(2026, 10), null);
        model.setUser(owner);
        return model;
    }

    // ---- applyReferences ----

    @Test
    void applyReferences_shouldAttachTheCategoryAndTheCardOfTheOwner_whenTheIdsAreInformed() {
        var model = modelOf("Spotify");
        when(categoryService.getByIdAndUser(categoryId, ownerId))
            .thenReturn(category);
        when(cardAccountService.getByIdAndUser(cardId, ownerId))
            .thenReturn(card);

        recurringExpenseMapper.applyReferences(model, RecurringExpenseCommand.of(model).withCategoryId(categoryId).withCardAccountId(cardId));

        assertSame(category, model.getCategory());
        assertSame(card, model.getCardAccount());
    }

    // A categoria por nome é resolvida (e criada, se faltar) entre as do dono; o id nem é consultado.
    @Test
    void applyReferences_shouldResolveOrCreateTheCategoryByName_whenOnlyTheNameIsInformed() {
        var model = modelOf("Ração");
        when(categoryService.getOrCreateByName(ownerId, "Pets"))
            .thenReturn(category);

        recurringExpenseMapper.applyReferences(model, RecurringExpenseCommand.of(model).withCategoryName("Pets"));

        assertSame(category, model.getCategory());
        verify(categoryService, never()).getByIdAndUser(any(), any());
    }

    @Test
    void applyReferences_shouldPreferTheCategoryId_whenTheNameIsAlsoInformed() {
        var model = modelOf("Spotify");
        when(categoryService.getByIdAndUser(categoryId, ownerId))
            .thenReturn(category);

        recurringExpenseMapper.applyReferences(model, RecurringExpenseCommand.of(model).withCategoryId(categoryId).withCategoryName("Pets"));

        assertSame(category, model.getCategory());
        verify(categoryService, never()).getOrCreateByName(any(), any());
    }

    @Test
    void applyReferences_shouldLeaveTheCardEmpty_whenNoCardIsInformed() {
        var model = modelOf("Aluguel");
        when(categoryService.getByIdAndUser(categoryId, ownerId))
            .thenReturn(category);

        recurringExpenseMapper.applyReferences(model, RecurringExpenseCommand.of(model).withCategoryId(categoryId));

        assertNull(model.getCardAccount());
        verifyNoInteractions(cardAccountService);
    }

    // Na alteração, tirar o cartão do comando desvincula o que o modelo já tinha.
    @Test
    void applyReferences_shouldDetachTheCard_whenTheModelHadOneAndNoneIsInformed() {
        var model = modelOf("Spotify");
        model.setCardAccount(card);
        when(categoryService.getByIdAndUser(categoryId, ownerId))
            .thenReturn(category);

        recurringExpenseMapper.applyReferences(model, RecurringExpenseCommand.of(model).withCategoryId(categoryId));

        assertNull(model.getCardAccount());
    }

    // O 404 vem de getByIdAndUser, que só enxerga o que é do dono (CategoryRepositoryTest / CardAccountRepositoryTest).
    @Test
    void applyReferences_shouldPropagateNotFound_whenTheCategoryBelongsToAnotherUser() {
        var model = modelOf("Spotify");
        when(categoryService.getByIdAndUser(categoryId, ownerId))
            .thenThrow(new ResourceNotFoundException("Categoria não encontrada"));
        var command = RecurringExpenseCommand.of(model).withCategoryId(categoryId);

        assertThrows(ResourceNotFoundException.class, () -> recurringExpenseMapper.applyReferences(model, command));
        assertNull(model.getCategory());
    }

    @Test
    void applyReferences_shouldPropagateNotFound_whenTheCardBelongsToAnotherUser() {
        var model = modelOf("Spotify");
        when(categoryService.getByIdAndUser(categoryId, ownerId))
            .thenReturn(category);
        when(cardAccountService.getByIdAndUser(cardId, ownerId))
            .thenThrow(new ResourceNotFoundException("Conta/Cartão não encontrado"));
        var command = RecurringExpenseCommand.of(model).withCategoryId(categoryId).withCardAccountId(cardId);

        assertThrows(ResourceNotFoundException.class, () -> recurringExpenseMapper.applyReferences(model, command));
    }

    // ---- applyEditableFields ----

    @Test
    void applyEditableFields_shouldCopyTheFiveEditableFields() {
        var target = modelOf("Spotify");
        var source = RecurringExpense.of("Spotify Família", BigDecimal.valueOf(34.90), 10, YearMonth.of(2026, 11), YearMonth.of(2027, 5));

        recurringExpenseMapper.applyEditableFields(target, source);

        assertEquals("Spotify Família", target.getDescription());
        assertEquals(BigDecimal.valueOf(34.90), target.getValue());
        assertEquals(10, target.getDayOfMonth());
        assertEquals(LocalDate.of(2026, 11, 1), target.getStartMonth());
        assertEquals(LocalDate.of(2027, 5, 1), target.getEndMonth());
    }

    @Test
    void applyEditableFields_shouldClearTheEndMonth_whenTheSourceHasNone() {
        var target = RecurringExpense.of("Spotify", BigDecimal.valueOf(21.90), 5, YearMonth.of(2026, 10), YearMonth.of(2027, 3));
        var source = RecurringExpense.of("Spotify", BigDecimal.valueOf(21.90), 5, YearMonth.of(2026, 10), null);

        recurringExpenseMapper.applyEditableFields(target, source);

        assertNull(target.getEndMonth());
    }

    // Dono, categoria, cartão e id não são editáveis por aqui (as referências têm a própria operação).
    @Test
    void applyEditableFields_shouldKeepTheOwnerTheReferencesAndTheId() {
        var target = modelOf("Spotify");
        target.setId(UUID.randomUUID());
        target.setCategory(category);
        target.setCardAccount(card);
        var id = target.getId();
        var source = RecurringExpense.of("Outro", BigDecimal.TEN, 20, YearMonth.of(2027, 1), null);
        source.setUser(User.builder().name("Outra pessoa").build());

        recurringExpenseMapper.applyEditableFields(target, source);

        assertSame(owner, target.getUser());
        assertSame(category, target.getCategory());
        assertSame(card, target.getCardAccount());
        assertEquals(id, target.getId());
    }

}
