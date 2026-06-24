package br.com.finc2u.server.features.card.repository;

import br.com.finc2u.server.features.card.entity.CardAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CardAccountRepository extends JpaRepository<CardAccount, UUID> {

    List<CardAccount> findByUserId(UUID userId);

    List<CardAccount> findByUserIdOrderByCardNameAsc(UUID userId);

    List<CardAccount> findByUserIdAndCardNameContainingIgnoreCase(UUID userId, String cardName);

}
