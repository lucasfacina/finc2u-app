package br.com.finc2u.server.features.extra.repository;

import br.com.finc2u.server.features.extra.entity.Extra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExtraRepository extends JpaRepository<Extra, UUID> {

    List<Extra> findByUserId(UUID userId);

    List<Extra> findByUserIdAndDateBetween(UUID userId, LocalDate start, LocalDate end);

}
