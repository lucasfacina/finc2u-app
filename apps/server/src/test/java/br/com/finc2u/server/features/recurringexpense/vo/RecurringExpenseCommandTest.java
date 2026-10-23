package br.com.finc2u.server.features.recurringexpense.vo;

import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RecurringExpenseCommandTest {

    private final RecurringExpense model = RecurringExpense.of("Spotify", new BigDecimal("21.90"), 5, YearMonth.of(2026, 10), null);

    @Test
    void of_shouldStartWithoutAnyReference() {
        var command = RecurringExpenseCommand.of(model);

        assertSame(model, command.recurringExpense());
        assertNull(command.categoryId());
        assertNull(command.categoryName());
        assertNull(command.cardAccountId());
    }

    @Test
    void with_shouldSetOnlyItsOwnFieldAndKeepTheOthers() {
        var categoryId = UUID.randomUUID();
        var cardId = UUID.randomUUID();

        var command = RecurringExpenseCommand.of(model)
            .withCategoryId(categoryId)
            .withCategoryName("Assinaturas")
            .withCardAccountId(cardId);

        assertEquals(new RecurringExpenseCommand(model, categoryId, "Assinaturas", cardId), command);
    }

    @Test
    void with_shouldNotChangeTheOriginalCommand() {
        var original = RecurringExpenseCommand.of(model);

        original.withCardAccountId(UUID.randomUUID());

        assertNull(original.cardAccountId());
    }

}
