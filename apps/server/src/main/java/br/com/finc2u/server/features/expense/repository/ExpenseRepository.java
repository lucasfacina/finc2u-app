package br.com.finc2u.server.features.expense.repository;

import br.com.finc2u.server.features.expense.entity.Expense;
import br.com.finc2u.server.features.expense.enums.ExpenseType;
import br.com.finc2u.server.features.expense.enums.PaymentStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    @NonNull
    @EntityGraph(attributePaths = {"cardAccount", "category"}, type = EntityGraph.EntityGraphType.LOAD)
    @Override
    List<Expense> findAll(@NonNull Specification<Expense> specification);

    @NonNull
    @EntityGraph(attributePaths = {"cardAccount", "category"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<Expense> findById(@NonNull UUID id);

    @EntityGraph(attributePaths = {"cardAccount", "category"}, type = EntityGraph.EntityGraphType.LOAD)
    List<Expense> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    // Alimenta o resumo mensal: o cálculo por categoria lê a categoria (LAZY) e o cartão vem junto, então os dois entram no graph.
    @EntityGraph(attributePaths = {"cardAccount", "category"}, type = EntityGraph.EntityGraphType.LOAD)
    List<Expense> findByUserIdAndDueDateBetween(UUID userId, LocalDate start, LocalDate end);

    // As parcelas de uma compra (o groupId é gerado pelo servidor). Alimenta o ExpenseResponse: cartão e categoria no graph.
    @EntityGraph(attributePaths = {"cardAccount", "category"}, type = EntityGraph.EntityGraphType.LOAD)
    List<Expense> findByInstallmentGroupIdOrderByCurrentInstallment(UUID installmentGroupId);

    // Idempotência da recorrência: a despesa de um modelo num mês existe uma vez só (unique no banco), qualquer que seja o vencimento.
    boolean existsByRecurringExpenseIdAndRecurrenceMonth(UUID recurringExpenseId, LocalDate recurrenceMonth);

    // Apagar o modelo recorrente não apaga as despesas que ele gerou: elas ficam sem modelo (e sem o mês, que só faz sentido com ele).
    @Modifying
    @Query("update Expense e set e.recurringExpenseId = null, e.recurrenceMonth = null where e.recurringExpenseId = :recurringExpenseId")
    void detachFromRecurringExpense(@Param("recurringExpenseId") UUID recurringExpenseId);

    // Alimenta a projeção de parcelas futuras: o ExpenseResponse lê cartão e categoria (esta é LAZY), então ambos vêm no graph.
    @EntityGraph(attributePaths = {"cardAccount", "category"}, type = EntityGraph.EntityGraphType.LOAD)
    List<Expense> findByUserIdAndExpenseTypeAndPaymentStatusAndDueDateAfterOrderByDueDate(
        UUID userId, ExpenseType type, PaymentStatus status, LocalDate referenceDate);

}
