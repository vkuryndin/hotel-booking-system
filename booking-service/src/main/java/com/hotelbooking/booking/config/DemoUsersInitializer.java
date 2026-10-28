
package com.hotelbooking.booking.config;

import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;
import com.hotelbooking.booking.repository.AppUserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(
        name = "app.demo.enabled",
        havingValue = "true"
)
public class DemoUsersInitializer implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(DemoUsersInitializer.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final String firstPassword;
    private final String secondPassword;

    public DemoUsersInitializer(
            AppUserRepository users,
            PasswordEncoder passwords,
            @Value("${DEMO_USER_1_PASSWORD:}") String firstPassword,
            @Value("${DEMO_USER_2_PASSWORD:}") String secondPassword
    ) {
        this.users = users;
        this.passwords = passwords;
        this.firstPassword = firstPassword;
        this.secondPassword = secondPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        validatePassword(
                firstPassword, "DEMO_USER_1_PASSWORD"
        );
        validatePassword(
                secondPassword, "DEMO_USER_2_PASSWORD"
        );

        seed(
                "anna.petrova@example.com",
                "anna.petrova",
                "+37400000001",
                "Anna",
                "Petrova",
                firstPassword
        );

        seed(
                "david.sargsyan@example.com",
                "david.sargsyan",
                "+37400000002",
                "David",
                "Sargsyan",
                secondPassword
        );

        log.info("Two USER demo accounts initialized");
    }

    private void seed(
            String email,
            String username,
            String phone,
            String firstName,
            String lastName,
            String password
    ) {
        var existing = users.findByEmail(email);

        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.USER) {
                throw new IllegalStateException(
                        "Demo email belongs to non-USER: " + email
                );
            }
            return;
        }

        if (users.existsByUsername(username)) {
            throw new IllegalStateException(
                    "Demo username already in use: " + username
            );
        }

        users.save(new AppUser(
                email,
                passwords.encode(password),
                Role.USER,
                username,
                phone,
                firstName,
                lastName
        ));
    }

    private static void validatePassword(
            String password,
            String variable
    ) {
        int bytes = password
                .getBytes(StandardCharsets.UTF_8).length;

        if (password.length() < 8 || bytes > 72) {
            throw new IllegalStateException(
                    variable + " must contain 8 to 72 UTF-8 bytes"
            );
        }
    }
}
