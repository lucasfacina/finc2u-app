package br.com.finc2u.server.features.expense.filter;

import br.com.finc2u.server.features.card.entity.CardAccount;
import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ExpenseFilter(
        Integer month,
        Integer year,
        PaymentStatus status,
        ExpenseType type,
        UUID cardAccountId,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {

    public boolean matchesMonth(Expense expense) {
        return month == null || expense.getDueDate().getMonthValue() == month;
    }

    public boolean matchesYear(Expense expense) {
        return year == null || expense.getDueDate().getYear() == year;
    }

    public boolean matchesStatus(Expense expense) {
        return status == null || expense.getPaymentStatus() == status;
    }

    public boolean matchesType(Expense expense) {
        return type == null || expense.getExpenseType() == type;
    }

    public boolean matchesCard(Expense expense) {
        if (cardAccountId == null) {
            return true;
        }
        CardAccount card = expense.getCardAccount();
        return card != null && cardAccountId.equals(card.getId());
    }

    public boolean matchesPriceRange(Expense expense) {
        BigDecimal value = expense.getValue();
        return (minPrice == null || value.compareTo(minPrice) >= 0)
                && (maxPrice == null || value.compareTo(maxPrice) <= 0);
    }

    public boolean matches(Expense expense) {
        return matchesMonth(expense)
                && matchesYear(expense)
                && matchesStatus(expense)
                && matchesType(expense)
                && matchesCard(expense)
                && matchesPriceRange(expense);
    }

}
