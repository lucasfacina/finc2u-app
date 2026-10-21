package br.com.finc2u.server.features.summary.repository;

import br.com.finc2u.server.features.summary.entity.MonthlySummaryCategoryTotal;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MonthlySummaryCategoryTotalRepository extends JpaRepository<MonthlySummaryCategoryTotal, UUID> {

    // Os totais de vários resumos de uma vez (o histórico não faz uma consulta por resumo); a categoria vem no graph.
    @EntityGraph(attributePaths = {"category"}, type = EntityGraph.EntityGraphType.LOAD)
    List<MonthlySummaryCategoryTotal> findBySummaryIdInOrderByCategoryNameAsc(Collection<UUID> summaryIds);

    // No recálculo os totais antigos saem e entram os novos; ao apagar o resumo, os filhos saem antes (FK).
    @Modifying
    @Query("delete from MonthlySummaryCategoryTotal t where t.summary.id = :summaryId")
    void deleteBySummaryId(@Param("summaryId") UUID summaryId);

}
