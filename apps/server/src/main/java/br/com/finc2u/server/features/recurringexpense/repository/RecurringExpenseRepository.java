package br.com.finc2u.server.features.recurringexpense.repository;

import br.com.finc2u.server.features.recurringexpense.entity.RecurringExpense;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, UUID> {

    @EntityGraph(attributePaths = {"cardAccount", "category"}, type = EntityGraph.EntityGraphType.LOAD)
    List<RecurringExpense> findByUserIdOrderByDescriptionAsc(UUID userId);

}
