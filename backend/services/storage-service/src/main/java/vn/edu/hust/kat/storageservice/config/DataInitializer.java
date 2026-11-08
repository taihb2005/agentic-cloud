package vn.edu.hust.kat.storageservice.config;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hust.kat.storageservice.entity.FsNodePermission;
import vn.edu.hust.kat.storageservice.repository.FsNodePermissionRepository;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final FsNodePermissionRepository fsNodePermissionRepository;

    @Override
    @Transactional
    public void run(String... args) {
        FsNodePermission viewPermission= fsNodePermissionRepository.findByCode(FsNodePermission.PermissionCode.VIEW)
                .orElseGet(() -> fsNodePermissionRepository.save(FsNodePermission.builder().code(FsNodePermission.PermissionCode.VIEW).build()));

        FsNodePermission editPermission = fsNodePermissionRepository.findByCode(FsNodePermission.PermissionCode.EDIT)
                .orElseGet(() -> fsNodePermissionRepository.save(FsNodePermission.builder().code(FsNodePermission.PermissionCode.EDIT).build()));

        Set<FsNodePermission.PermissionCode> setOfPermission = Set.of(
                viewPermission.getCode(),
                editPermission.getCode()
        );

        log.info("✅ Tạo quyền trên node thành công {}", setOfPermission);
    }
}
