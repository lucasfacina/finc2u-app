package br.com.finc2u.server.features.recurringexpense.repository;

import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpenseSkip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface RecurringExpenseSkipRepository extends JpaRepository<RecurringExpenseSkip, UUID> {

    boolean existsByRecurringExpenseIdAndRecurrenceMonth(UUID recurringExpenseId, LocalDate recurrenceMonth);

    // Os pulos saem antes do modelo (FK): apagar o modelo apaga os pulos dele.
    @Modifying
    @Query("delete from RecurringExpenseSkip s where s.recurringExpense.id = :recurringExpenseId")
    void deleteByRecurringExpenseId(@Param("recurringExpenseId") UUID recurringExpenseId);

}
