package br.com.finc2u.server.features.recurringexpense.repository;

import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import jakarta.persistence.LockModeType;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, UUID> {

    @NonNull
    @EntityGraph(attributePaths = {"cardAccount", "category"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<RecurringExpense> findById(@NonNull UUID id);

    @EntityGraph(attributePaths = {"cardAccount", "category"}, type = EntityGraph.EntityGraphType.LOAD)
    List<RecurringExpense> findByUserIdOrderByDescriptionAsc(UUID userId);

    // Trava a linha do modelo até o fim da transação (SELECT ... FOR UPDATE): quem gera as despesas dele espera aqui pela outra transação.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecurringExpense r where r.id = :id")
    Optional<RecurringExpense> findByIdForUpdate(@Param("id") UUID id);

    // Exceção à regra de consulta escopada por usuário: devolve os modelos de TODOS, e só o job do sistema a usa.
    @EntityGraph(attributePaths = {"cardAccount", "category"}, type = EntityGraph.EntityGraphType.LOAD)
    List<RecurringExpense> findByEndMonthIsNullOrEndMonthGreaterThanEqual(LocalDate firstDayOfMonth);

}
