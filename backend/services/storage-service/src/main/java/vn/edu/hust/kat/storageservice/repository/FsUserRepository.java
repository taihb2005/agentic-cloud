package vn.edu.hust.kat.storageservice.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hust.kat.storageservice.entity.FsUser;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FsUserRepository extends JpaRepository<FsUser, UUID> {
    Optional<FsUser> findById(@NotNull UUID id);
    boolean existsById(@NotNull UUID id);

    @Modifying
    @Query(value = """
        UPDATE fs_users
          SET storage_current_quota_bytes = :quota,
              updated_at = LOCALTIMESTAMP
        WHERE id = :id
    """, nativeQuery = true)
    int updateCurrentQuota(
            @NotNull UUID id,
            @Param("quota") Long quota
    );

    @Modifying
    @Query(value = """
        UPDATE fs_users
          SET storage_current_quota_bytes = storage_current_quota_bytes + :quota,
              updated_at = LOCALTIMESTAMP
        WHERE id = :id
    """, nativeQuery = true)
    int addToCurrentQuota(
            @NotNull UUID id,
            @Param("quota") Long quota
    );

    @Modifying
    @Query(value = """
        UPDATE fs_users
          SET storage_current_quota_bytes = GREATEST(storage_current_quota_bytes - :quota, 0),
              updated_at = LOCALTIMESTAMP
        WHERE id = :id
    """, nativeQuery = true)
    int subtractToCurrentQuota(
            @NotNull UUID id,
            @Param("quota") Long quota
    );

    @Modifying
    @Query(value = """
        UPDATE fs_users
          SET storage_max_quota_bytes = :quota,
              updated_at = LOCALTIMESTAMP
        WHERE id = :id
    """, nativeQuery = true)
    int updateMaxQuota(
            @NotNull UUID id,
            @Param("quota") Long  quota
    );

    @Query("""
    SELECT CASE WHEN u.storageCurrentQuotaBytes + :uploadSize > u.storageMaxQuotaBytes
                THEN true
                ELSE false
           END
    FROM FsUser u
    WHERE u.id = :id
    """)
    boolean isQuotaExceeded(
            @NotNull UUID id,
            @Param("uploadSize") Long uploadSize
    );
}
