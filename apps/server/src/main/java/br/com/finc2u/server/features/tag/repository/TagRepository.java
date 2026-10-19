package br.com.finc2u.server.features.tag.repository;

import br.com.finc2u.server.features.tag.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findByUserIdOrderByNameAsc(UUID userId);

    Optional<Tag> findByUserIdAndNameIgnoreCase(UUID userId, String name);

    List<Tag> findByUserIdAndNameIn(UUID userId, Collection<String> names);

    List<Tag> findByIdInAndUserId(Collection<UUID> ids, UUID userId);

}
