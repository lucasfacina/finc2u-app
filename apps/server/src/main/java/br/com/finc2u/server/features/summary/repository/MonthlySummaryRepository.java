package br.com.finc2u.server.features.summary.repository;

import br.com.finc2u.server.features.summary.entity.MonthlySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonthlySummaryRepository extends JpaRepository<MonthlySummary, UUID> {

    List<MonthlySummary> findByUserId(UUID userId);

    Optional<MonthlySummary> findByUserIdAndMonthAndYear(UUID userId, Integer month, Integer year);

}
