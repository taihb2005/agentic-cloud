package vn.edu.hust.kat.userservice.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hust.kat.userservice.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Query("""
        SELECT DISTINCT u
        FROM User u
        LEFT JOIN FETCH u.roles
        WHERE u.id = :id
    """)
    Optional<User> findByIdWithRoles(@Param("id") UUID id);

    @Query("""
        SELECT u
        FROM User u
        WHERE (LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))
          AND (:callerId IS NULL OR u.id != :callerId)
          AND u.enabled = true
        ORDER BY u.username ASC
    """)
    List<User> searchUsers(@Param("query") String query, @Param("callerId") UUID callerId, Pageable pageable);
}
