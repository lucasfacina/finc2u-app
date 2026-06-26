package br.com.finc2u.server.features.expense.mapper;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.card.service.CardAccountService;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.tag.entity.Tag;
import br.com.finc2u.server.features.tag.service.TagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ExpenseMapperTest {

    @Mock
    private CardAccountService cardAccountService;

    @Mock
    private TagService tagService;

    @InjectMocks
    private ExpenseMapper expenseMapper;

    @Test
    void applyCardAccount_shouldResolveAndAttach_whenIdPresent() {
        UUID cardId = UUID.randomUUID();
        CardAccount card = CardAccount.builder()
                .build();
        card.setId(cardId);
        when(cardAccountService.getById(cardId)).thenReturn(card);

        Expense expense = Expense.builder()
                .build();
        expenseMapper.applyCardAccount(expense, cardId);

        assertSame(card, expense.getCardAccount());
    }

    @Test
    void applyCardAccount_shouldUnlink_whenIdNull() {
        Expense expense = Expense.builder()
                .cardAccount(CardAccount.builder().build())
                .build();

        expenseMapper.applyCardAccount(expense, null);

        assertNull(expense.getCardAccount());
        verifyNoInteractions(cardAccountService);
    }

    @Test
    void applyTags_shouldResolveAndAttach_whenListPresent() {
        List<UUID> ids = List.of(UUID.randomUUID());
        List<Tag> tags = List.of(Tag.builder()
                .build());
        when(tagService.getByIds(ids)).thenReturn(tags);

        Expense expense = Expense.builder()
                .build();
        expenseMapper.applyTags(expense, ids);

        assertEquals(tags, expense.getTags());
    }

    @Test
    void applyTags_shouldDoNothing_whenListNull() {
        Expense expense = Expense.builder()
                .build();

        expenseMapper.applyTags(expense, null);

        assertNull(expense.getTags());
        verifyNoInteractions(tagService);
    }

    @Test
    void applyEditableFields_shouldCopyScalarFields_butNotAssociations() {
        CardAccount card = CardAccount.builder().
                build();
        Expense target = Expense.builder()
                .description("antigo")
                .value(BigDecimal.valueOf(10))
                .cardAccount(card)
                .build();

        Expense source = Expense.builder()
                .description("novo")
                .value(BigDecimal.valueOf(99))
                .dueDate(LocalDate.of(2026, 8, 1))
                .expenseType(ExpenseType.PARCELED)
                .paymentStatus(PaymentStatus.PAID)
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
        assertEquals(2, target.getCurrentInstallment());
        assertEquals(5, target.getTotalInstallment());
        assertSame(card, target.getCardAccount());
    }

}
