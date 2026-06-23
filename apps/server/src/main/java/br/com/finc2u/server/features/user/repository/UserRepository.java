package br.com.finc2u.server.features.user.repository;

import br.com.finc2u.server.features.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    List<User> findByNameContainingIgnoreCase(String name);

    Optional<User> findFirstByOrderByCreatedAtDesc();

}
