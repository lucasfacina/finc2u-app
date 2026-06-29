package br.com.finc2u.server.features.expense.repository;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    @NonNull
    @EntityGraph(attributePaths = {"tags"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<Expense> findById(@NonNull UUID id);

    @EntityGraph(attributePaths = {"tags"}, type = EntityGraph.EntityGraphType.LOAD)
    List<Expense> findByUserId(UUID userId);

    List<Expense> findByCardAccountId(UUID cardAccountId);

    List<Expense> findByPaymentStatus(PaymentStatus status);

    List<Expense> findByExpenseTypeAndPaymentStatus(ExpenseType type, PaymentStatus status);

    List<Expense> findByPaymentStatusOrExpenseType(PaymentStatus status, ExpenseType type);

    List<Expense> findByCardAccountIdIn(List<UUID> cardAccountIds);

    List<Expense> findByUserIdOrderByValueDesc(UUID userId);

    List<Expense> findByDueDateBetween(LocalDate start, LocalDate end);

    List<Expense> findByUserIdAndDueDateBetween(UUID userId, LocalDate start, LocalDate end);

    List<Expense> findByValueLessThan(BigDecimal value);

    List<Expense> findByValueLessThanEqual(BigDecimal value);

    List<Expense> findByValueGreaterThan(BigDecimal value);

    List<Expense> findByValueGreaterThanEqual(BigDecimal value);

    List<Expense> findByExpenseTypeAndUserId(ExpenseType type, UUID userId);

}
