package vn.edu.hust.kat.userservice.repository;

import vn.edu.hust.kat.userservice.entity.RefreshToken;
import vn.edu.hust.kat.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokesAt = local datetime WHERE rt.user = :user")
    void revokeAllByUser(User user);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.revokesAt != null")
    void deleteExpiredAndRevoked();
}
