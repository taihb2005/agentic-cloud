package vn.edu.hust.kat.storageservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hust.kat.storageservice.entity.FsUser;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FsUserRepository extends JpaRepository<FsUser, UUID> {
    Optional<FsUser> findById(UUID id);
    boolean existsById(UUID id);
}
