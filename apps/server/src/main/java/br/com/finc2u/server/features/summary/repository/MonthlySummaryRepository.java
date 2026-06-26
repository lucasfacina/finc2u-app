package br.com.finc2u.server.features.summary.repository;

import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import br.com.finc2u.server.features.summary.entity.MonthlySummaryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonthlySummaryRepository extends JpaRepository<MonthlySummary, MonthlySummaryId> {

    List<MonthlySummary> findByUserId(UUID userId);

    Optional<MonthlySummary> findByUserIdAndIdMonthAndIdYear(UUID userId, Integer month, Integer year);

    Optional<MonthlySummary> findFirstByUserIdOrderByIdYearDescIdMonthDesc(UUID userId);

    List<MonthlySummary> findByUserIdAndIdYear(UUID userId, Integer year);

    List<MonthlySummary> findByRemainingAmountLessThan(BigDecimal value);

    List<MonthlySummary> findByRemainingAmountGreaterThanEqual(BigDecimal value);

    List<MonthlySummary> findByIdYearAndIdMonthBetween(Integer year, Integer monthStart, Integer monthEnd);

}
