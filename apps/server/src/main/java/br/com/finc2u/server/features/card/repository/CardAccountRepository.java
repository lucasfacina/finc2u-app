package br.com.finc2u.server.features.card.repository;

import br.com.finc2u.server.features.card.entity.CardAccount;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardAccountRepository extends JpaRepository<CardAccount, UUID> {

    List<CardAccount> findByUserId(UUID userId);

    // Alimenta o CardAccountResponse, que lê o nome do dono (LAZY): o usuário vem no graph.
    @EntityGraph(attributePaths = {"user"}, type = EntityGraph.EntityGraphType.LOAD)
    List<CardAccount> findByUserIdAndCardNameContainingIgnoreCaseOrderByCardNameAsc(UUID userId, String cardName);

    @NonNull
    @EntityGraph(attributePaths = {"user"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<CardAccount> findById(@NonNull UUID id);

    Optional<CardAccount> findByUserIdAndCardNameIgnoreCase(UUID userId, String cardName);

    Optional<CardAccount> findByIdAndUserId(UUID id, UUID userId);

}
