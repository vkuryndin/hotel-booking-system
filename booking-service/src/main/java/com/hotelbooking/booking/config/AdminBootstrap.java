package com.hotelbooking.booking.config;

import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;
import com.hotelbooking.booking.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${ADMIN_EMAIL:}") String adminEmail,
            @Value("${ADMIN_PASSWORD:}") String adminPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {

        if (adminEmail.isBlank() && adminPassword.isBlank()) {
            return;
        }


        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "Both ADMIN_EMAIL and ADMIN_PASSWORD must be configured"
            );
        }

        String email = adminEmail.trim().toLowerCase(Locale.ROOT);

        String bootstrapUsername = "admin-" +
                java.util.UUID.nameUUIDFromBytes(
                        email.getBytes(StandardCharsets.UTF_8)
                ).toString().substring(0, 12);

        if (!email.contains("@")
                || adminPassword.length() < 8
                || adminPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Invalid admin credentials configuration");
        }

        userRepository.findByEmail(email).ifPresentOrElse(
                existing -> {
                    if (existing.getRole() != Role.ADMIN) {
                        throw new IllegalStateException(
                                "Admin email already belongs to a regular user"
                        );
                    }
                },
                () -> userRepository.save(
                        new AppUser(
                                email,
                                passwordEncoder.encode(adminPassword),
                                Role.ADMIN,
                                bootstrapUsername,
                                null,
                                "System",
                                "Administrator"
                        )
                )
        );
    }
}