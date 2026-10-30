package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.request.LoginRequest;
import com.hotelbooking.booking.dto.response.LoginResponse;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.repository.AppUserRepository;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class LoginService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private com.hotelbooking.booking.security.FailedAccountLoginLimiter failedAccountLimiter;

    @org.springframework.beans.factory.annotation.Autowired
    void setFailedAccountLimiter(com.hotelbooking.booking.security.FailedAccountLoginLimiter limiter) {
        this.failedAccountLimiter = limiter;
    }
    // Same BCrypt work for unknown email as for wrong password.
    private static final String DUMMY_PASSWORD_HASH = "$2a$12$As7ZNhOUfphTzrrNdISEpuLNRxDV8rdqgtXAF8WKN7kNzN6NaoOhC";

    public LoginService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (failedAccountLimiter != null) {
            long retry = failedAccountLimiter.retryAfter(email);
            if (retry > 0) {
                throw new com.hotelbooking.booking.exception.RateLimitExceededException(retry);
            }
        }

        AppUser user = userRepository.findByEmail(email).orElse(null);
        String hash = user == null ? DUMMY_PASSWORD_HASH : user.getPasswordHash();
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !matches) {
            if (failedAccountLimiter != null) {
                failedAccountLimiter.recordFailure(email);
            }

            throw new BadCredentialsException(
                    "Invalid email or password"
            );
        }

        if (failedAccountLimiter != null) {
            failedAccountLimiter.clear(email);
        }
        String token = jwtService.generateAccessToken(user);

        return new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpiresInSeconds(),
                user.isPasswordChangeRequired()
        );
    }
}