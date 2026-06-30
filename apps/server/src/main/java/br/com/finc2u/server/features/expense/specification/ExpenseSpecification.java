package br.com.finc2u.server.features.expense.specification;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import br.com.finc2u.server.features.expense.filter.ExpenseFilter;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/*
 * Fornece métodos para criar Specifications do JPA para filtrar entidades Expense. Ela permite a filtragem com base no
 * ID do usuário, período de datas, status de pagamento, tipo de despesa, ID da conta do cartão e faixa de preço.
 */
public class ExpenseSpecification {

    private ExpenseSpecification() {
    }

    public static Specification<Expense> ofFilter(UUID userId, ExpenseFilter filter) {
        return Specification
                .where(hasUser(userId))
                .and(inPeriod(filter.month(), filter.year()))
                .and(hasStatus(filter.status()))
                .and(hasType(filter.type()))
                .and(hasCard(filter.cardAccountId()))
                .and(minPrice(filter.minPrice()))
                .and(maxPrice(filter.maxPrice()));
    }

    private static Specification<Expense> hasUser(UUID userId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("user").get("id"), userId);
    }

    private static Specification<Expense> inPeriod(Integer month, Integer year) {
        if (year == null) return null;
        LocalDate start, end;
        if (month != null) {
            start = LocalDate.of(year, month, 1);
            end = start.withDayOfMonth(start.lengthOfMonth());
        } else {
            start = LocalDate.of(year, 1, 1);
            end = LocalDate.of(year, 12, 31);
        }
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.between(root.get("dueDate"), start, end);
    }

    private static Specification<Expense> hasStatus(PaymentStatus status) {
        if (status == null) return null;
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("paymentStatus"), status);
    }

    private static Specification<Expense> hasType(ExpenseType type) {
        if (type == null) return null;
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("expenseType"), type);
    }

    private static Specification<Expense> hasCard(UUID cardAccountId) {
        if (cardAccountId == null) return null;
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("cardAccount").get("id"), cardAccountId);
    }

    private static Specification<Expense> minPrice(BigDecimal min) {
        if (min == null) return null;
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get("value"), min);
    }

    private static Specification<Expense> maxPrice(BigDecimal max) {
        if (max == null) return null;
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(root.get("value"), max);
    }

}
