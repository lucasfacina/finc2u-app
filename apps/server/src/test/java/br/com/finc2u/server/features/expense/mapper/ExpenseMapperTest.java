package br.com.finc2u.server.features.expense.mapper;

import br.com.finc2u.server.exception.ResourceNotFoundException;
import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.card.service.CardAccountService;
import br.com.finc2u.server.features.category.entity.Category;
import br.com.finc2u.server.features.category.service.CategoryService;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExpenseMapperTest {

    @Mock
    private CardAccountService cardAccountService;

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private ExpenseMapper expenseMapper;

    @Test
    void applyCardAccount_shouldResolveTheCardOfTheExpenseOwnerAndAttach_whenIdPresent() {
        var owner = User.builder()
            .build();
        owner.setId(UUID.randomUUID());
        var cardId = UUID.randomUUID();
        var card = CardAccount.builder()
            .build();
        card.setId(cardId);
        when(cardAccountService.getByIdAndUser(cardId, owner.getId()))
            .thenReturn(card);

        var expense = Expense.builder()
            .user(owner)
            .build();
        expenseMapper.applyCardAccount(expense, cardId, null);

        assertSame(card, expense.getCardAccount());
    }

    @Test
    void applyCardAccount_shouldPropagateNotFound_whenCardDoesNotBelongToTheOwner() {
        var owner = User.builder()
            .build();
        owner.setId(UUID.randomUUID());
        var cardId = UUID.randomUUID();
        when(cardAccountService.getByIdAndUser(cardId, owner.getId()))
            .thenThrow(new ResourceNotFoundException("Conta/Cartão não encontrado com o ID: " + cardId));

        var expense = Expense.builder()
            .user(owner)
            .build();

        assertThrows(ResourceNotFoundException.class, () -> expenseMapper.applyCardAccount(expense, cardId, null));
        assertNull(expense.getCardAccount());
    }

    @Test
    void applyCardAccount_shouldResolveTheCardByNameAmongTheOwnerCards_whenOnlyNamePresent() {
        var owner = User.builder()
            .build();
        owner.setId(UUID.randomUUID());
        var card = CardAccount.builder()
            .cardName("Nubank")
            .build();
        when(cardAccountService.getByUserAndName(owner.getId(), "nubank"))
            .thenReturn(card);

        var expense = Expense.builder()
            .user(owner)
            .build();
        expenseMapper.applyCardAccount(expense, null, "nubank");

        assertSame(card, expense.getCardAccount());
        verify(cardAccountService, never()).getByIdAndUser(any(), any());
    }

    @Test
    void applyCardAccount_shouldPropagateNotFound_whenCardNameDoesNotBelongToTheOwner() {
        var owner = User.builder()
            .build();
        owner.setId(UUID.randomUUID());
        when(cardAccountService.getByUserAndName(owner.getId(), "Nubank"))
            .thenThrow(new ResourceNotFoundException("Conta/Cartão não encontrado com o nome: Nubank"));

        var expense = Expense.builder()
            .user(owner)
            .build();

        assertThrows(ResourceNotFoundException.class, () -> expenseMapper.applyCardAccount(expense, null, "Nubank"));
        assertNull(expense.getCardAccount());
    }

    @Test
    void applyCardAccount_shouldUnlink_whenNameIsBlank() {
        var expense = Expense.builder()
            .cardAccount(CardAccount.builder().build())
            .build();

        expenseMapper.applyCardAccount(expense, null, "  ");

        assertNull(expense.getCardAccount());
        verifyNoInteractions(cardAccountService);
    }

    @Test
    void applyCardAccount_shouldUnlink_whenIdNull() {
        var expense = Expense.builder()
            .cardAccount(CardAccount.builder().build())
            .build();

        expenseMapper.applyCardAccount(expense, null, null);

        assertNull(expense.getCardAccount());
        verifyNoInteractions(cardAccountService);
    }

    @Test
    void applyCategory_shouldResolveTheCategoryOfTheExpenseOwnerAndAttach() {
        var owner = User.builder()
            .build();
        owner.setId(UUID.randomUUID());
        var categoryId = UUID.randomUUID();
        var category = Category.of("Alimentação");
        category.setId(categoryId);
        when(categoryService.getByIdAndUser(categoryId, owner.getId()))
            .thenReturn(category);

        var expense = Expense.builder()
            .user(owner)
            .build();
        expenseMapper.applyCategory(expense, categoryId, null);

        assertSame(category, expense.getCategory());
    }

    @Test
    void applyCategory_shouldPropagateNotFound_whenCategoryDoesNotBelongToTheOwner() {
        var owner = User.builder()
            .build();
        owner.setId(UUID.randomUUID());
        var categoryId = UUID.randomUUID();
        when(categoryService.getByIdAndUser(categoryId, owner.getId()))
            .thenThrow(new ResourceNotFoundException("Categoria não encontrada com o ID: " + categoryId));

        var expense = Expense.builder()
            .user(owner)
            .build();

        assertThrows(ResourceNotFoundException.class, () -> expenseMapper.applyCategory(expense, categoryId, null));
        assertNull(expense.getCategory());
    }

    // Por nome, a categoria do dono é resolvida (e criada na hora, se faltar) pelo CategoryService.
    @Test
    void applyCategory_shouldResolveOrCreateTheCategoryByName_whenOnlyTheNameIsInformed() {
        var owner = User.builder()
            .build();
        owner.setId(UUID.randomUUID());
        var category = Category.of("Pets");
        when(categoryService.getOrCreateByName(owner.getId(), "Pets"))
            .thenReturn(category);

        var expense = Expense.builder()
            .user(owner)
            .build();
        expenseMapper.applyCategory(expense, null, "Pets");

        assertSame(category, expense.getCategory());
        verify(categoryService, never()).getByIdAndUser(any(), any());
    }

    @Test
    void applyCategory_shouldUseTheId_whenBothComeAndNeverCreateAnything() {
        var owner = User.builder()
            .build();
        owner.setId(UUID.randomUUID());
        var categoryId = UUID.randomUUID();
        var category = Category.of("Alimentação");
        when(categoryService.getByIdAndUser(categoryId, owner.getId()))
            .thenReturn(category);

        var expense = Expense.builder()
            .user(owner)
            .build();
        expenseMapper.applyCategory(expense, categoryId, "Pets");

        assertSame(category, expense.getCategory());
        verify(categoryService, never()).getOrCreateByName(any(), any());
    }

    @Test
    void applyEditableFields_shouldCopyScalarFields_butNotAssociations() {
        var card = CardAccount.builder().
            build();

        var target = Expense.builder()
            .description("antigo")
            .value(BigDecimal.valueOf(10))
            .cardAccount(card)
            .build();

        var source = Expense.builder()
            .description("novo")
            .value(BigDecimal.valueOf(99))
            .dueDate(LocalDate.of(2026, 8, 1))
            .expenseType(ExpenseType.PARCELED)
            .paymentStatus(PaymentStatus.PAID)
            .paidAt(LocalDate.of(2026, 7, 30))
            .currentInstallment(2)
            .totalInstallment(5)
            .cardAccount(CardAccount.builder().build())
            .build();

        expenseMapper.applyEditableFields(target, source);

        assertEquals("novo", target.getDescription());
        assertEquals(0, BigDecimal.valueOf(99).compareTo(target.getValue()));
        assertEquals(LocalDate.of(2026, 8, 1), target.getDueDate());
        assertEquals(ExpenseType.PARCELED, target.getExpenseType());
        assertEquals(PaymentStatus.PAID, target.getPaymentStatus());
        assertEquals(LocalDate.of(2026, 7, 30), target.getPaidAt());
        assertEquals(2, target.getCurrentInstallment());
        assertEquals(5, target.getTotalInstallment());
        assertSame(card, target.getCardAccount());
    }

}
