package vn.edu.hust.kat.storageservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hust.kat.storageservice.entity.FsNodePermission;

import java.util.Optional;

@Repository
public interface FsNodePermissionRepository extends JpaRepository<FsNodePermission, Long> {

    Optional<FsNodePermission> findByCode(FsNodePermission.PermissionCode code);

    default Optional<FsNodePermission> findByCodeString(String codeStr) {
        if (codeStr == null) return Optional.empty();
        try {
            return findByCode(FsNodePermission.PermissionCode.valueOf(codeStr.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
