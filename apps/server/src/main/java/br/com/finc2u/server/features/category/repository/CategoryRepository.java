package br.com.finc2u.server.features.category.repository;

import br.com.finc2u.server.features.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByUserIdOrderByNameAsc(UUID userId);

    Optional<Category> findByUserIdAndNameIgnoreCase(UUID userId, String name);

    Optional<Category> findByIdAndUserId(UUID id, UUID userId);

}
