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
 * ID do usuário, período de datas, status de pagamento, tipo de despesa, ID da conta do cartão, ID da categoria, faixa de
 * preço e atraso (pendente com vencimento anterior à data de referência).
 * Critério não informado vira Specification.unrestricted() (sem restrição) — o Spring Data JPA 4 não aceita null.
 */
public class ExpenseSpecification {

    private ExpenseSpecification() {
    }

    // referenceDate é "hoje" (vem do Clock do service): a especificação não lê o relógio.
    public static Specification<Expense> ofFilter(UUID userId, ExpenseFilter filter, LocalDate referenceDate) {
        return Specification
            .where(hasUser(userId))
            .and(inPeriod(filter.month(), filter.year()))
            .and(hasStatus(filter.status()))
            .and(hasType(filter.type()))
            .and(hasCard(filter.cardAccountId()))
            .and(hasCategory(filter.categoryId()))
            .and(minPrice(filter.minPrice()))
            .and(maxPrice(filter.maxPrice()))
            .and(isOverdue(filter.overdue(), referenceDate));
    }

    private static Specification<Expense> hasUser(UUID userId) {
        return (root, query, criteriaBuilder) ->
            criteriaBuilder.equal(root.get("user").get("id"), userId);
    }

    private static Specification<Expense> inPeriod(Integer month, Integer year) {
        if (year == null) return Specification.unrestricted();
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
        if (status == null) return Specification.unrestricted();
        return (root, query, criteriaBuilder) ->
            criteriaBuilder.equal(root.get("paymentStatus"), status);
    }

    private static Specification<Expense> hasType(ExpenseType type) {
        if (type == null) return Specification.unrestricted();
        return (root, query, criteriaBuilder) ->
            criteriaBuilder.equal(root.get("expenseType"), type);
    }

    private static Specification<Expense> hasCard(UUID cardAccountId) {
        if (cardAccountId == null) return Specification.unrestricted();
        return (root, query, criteriaBuilder) ->
            criteriaBuilder.equal(root.get("cardAccount").get("id"), cardAccountId);
    }

    private static Specification<Expense> hasCategory(UUID categoryId) {
        if (categoryId == null) return Specification.unrestricted();
        return (root, query, criteriaBuilder) ->
            criteriaBuilder.equal(root.get("category").get("id"), categoryId);
    }

    private static Specification<Expense> minPrice(BigDecimal min) {
        if (min == null) return Specification.unrestricted();
        return (root, query, criteriaBuilder) ->
            criteriaBuilder.greaterThanOrEqualTo(root.get("value"), min);
    }

    private static Specification<Expense> isOverdue(Boolean overdue, LocalDate referenceDate) {
        if (!Boolean.TRUE.equals(overdue)) return Specification.unrestricted();
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
            criteriaBuilder.equal(root.get("paymentStatus"), PaymentStatus.PENDING),
            criteriaBuilder.lessThan(root.get("dueDate"), referenceDate));
    }

    private static Specification<Expense> maxPrice(BigDecimal max) {
        if (max == null) return Specification.unrestricted();
        return (root, query, criteriaBuilder) ->
            criteriaBuilder.lessThanOrEqualTo(root.get("value"), max);
    }

}
