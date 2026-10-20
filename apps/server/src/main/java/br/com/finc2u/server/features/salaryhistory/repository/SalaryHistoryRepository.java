package br.com.finc2u.server.features.salaryhistory.repository;

import br.com.finc2u.server.features.salaryhistory.entity.SalaryHistory;
import br.com.finc2u.server.features.salaryhistory.vo.CurrentSalary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SalaryHistoryRepository extends JpaRepository<SalaryHistory, UUID> {

    Optional<SalaryHistory> findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(UUID userId, LocalDate date);

    List<SalaryHistory> findByUserIdOrderByEffectiveFromDesc(UUID userId);

    boolean existsByUserId(UUID userId);

    boolean existsByUserIdAndEffectiveFrom(UUID userId, LocalDate effectiveFrom);

    Optional<SalaryHistory> findByUserIdAndEffectiveFrom(UUID userId, LocalDate effectiveFrom);

    /*
     * Vigência mais recente (effectiveFrom <= date) de cada usuário informado, em uma única consulta.
     * Projeta só (userId, baseSalary) para não carregar o User de cada vigência.
     */
    @Query("""
        select new br.com.finc2u.server.features.salaryhistory.vo.CurrentSalary(h.user.id, h.baseSalary)
        from SalaryHistory h
        where h.user.id in :userIds
          and h.effectiveFrom = (
              select max(latest.effectiveFrom)
              from SalaryHistory latest
              where latest.user.id = h.user.id
                and latest.effectiveFrom <= :date)
        """)
    List<CurrentSalary> findCurrentSalaries(@Param("userIds") Collection<UUID> userIds, @Param("date") LocalDate date);

}
