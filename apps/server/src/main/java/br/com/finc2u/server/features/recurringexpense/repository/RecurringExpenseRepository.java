package br.com.finc2u.server.features.recurringexpense.repository;

import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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

}
