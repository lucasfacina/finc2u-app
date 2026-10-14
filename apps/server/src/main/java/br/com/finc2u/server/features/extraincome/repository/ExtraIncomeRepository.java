package br.com.finc2u.server.features.extraincome.repository;

import br.com.finc2u.server.features.extraincome.entity.ExtraIncome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExtraIncomeRepository extends JpaRepository<ExtraIncome, UUID> {

    List<ExtraIncome> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    List<ExtraIncome> findByUserIdAndDateBetween(UUID userId, LocalDate start, LocalDate end);

}
