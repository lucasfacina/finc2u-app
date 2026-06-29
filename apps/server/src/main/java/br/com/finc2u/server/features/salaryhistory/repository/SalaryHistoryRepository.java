package br.com.finc2u.server.features.salaryhistory.repository;

import br.com.finc2u.server.features.salaryhistory.entity.SalaryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SalaryHistoryRepository extends JpaRepository<SalaryHistory, UUID> {

    Optional<SalaryHistory> findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(UUID userId, LocalDate date);

    List<SalaryHistory> findByUserIdOrderByEffectiveFromDesc(UUID userId);

    boolean existsByUserId(UUID userId);

}
