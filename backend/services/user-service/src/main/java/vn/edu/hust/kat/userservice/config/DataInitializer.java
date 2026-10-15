package vn.edu.hust.kat.userservice.config;

import vn.edu.hust.kat.userservice.entity.Role;
import vn.edu.hust.kat.userservice.entity.User;
import vn.edu.hust.kat.userservice.repository.RoleRepository;
import vn.edu.hust.kat.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    private final Environment environment;
    @Value("${app.admin.email:admin@example.com}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@1234}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        // DataInitializer mein add karo:
        if (adminPassword.equals("Admin@1234")) {
            log.warn("⚠️  DEFAULT ADMIN PASSWORD DETECTED!");
            log.warn("⚠️  Set ADMIN_PASSWORD env var before going to prod!");

            boolean isProdProfile = Arrays.asList(
                    environment.getActiveProfiles()
            ).contains("prod");
            // prod profile mein → app band kar do
            if (isProdProfile) {
                throw new IllegalStateException(
                        "Default admin password in production! " +
                                "Set ADMIN_PASSWORD environment variable."
                );
            }
        }
        if (!userRepository.existsByEmail(adminEmail)) {
            Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_ADMIN").build()));
            Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").build()));

            User admin = User.builder()
                .username("Admin")
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .roles(Set.of(adminRole, userRole))
                .build();

            userRepository.save(admin);
            log.info("✅ Admin user created: {}", adminEmail);
        }
    }
}
