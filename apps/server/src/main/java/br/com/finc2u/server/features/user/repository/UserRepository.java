package br.com.finc2u.server.features.user.repository;

import br.com.finc2u.server.features.user.entity.User;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @NonNull
    @EntityGraph(attributePaths = {"userConfiguration"})
    @Override
    List<User> findAll();

    @EntityGraph(attributePaths = {"userConfiguration"})
    Optional<User> findByEmail(String email);

}
