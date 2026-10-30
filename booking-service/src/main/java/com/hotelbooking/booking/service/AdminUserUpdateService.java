package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.request.ResetAdminPasswordRequest;
import com.hotelbooking.booking.dto.request.UpdateAdminUserRequest;
import com.hotelbooking.booking.dto.response.AdminUserResponse;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.repository.AppUserRepository;

import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hotelbooking.booking.exception.UserOperationException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import com.hotelbooking.booking.exception.UsernameAlreadyRegisteredException;
import com.hotelbooking.booking.entity.Role;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
@Service
public class AdminUserUpdateService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserUpdateService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AdminUserResponse updateUser(
            Long id,
            UpdateAdminUserRequest request,
            Long actingAdminId
    ) {
        List<AppUser> lockedAdmins = request.role() == Role.USER
                ? userRepository.lockUsersByRole(Role.ADMIN)
                : List.of();

        AppUser user = findUser(id);

        if (request.email() == null
                && request.role() == null
                && request.username() == null
                && request.phone() == null
                && request.firstName() == null
                && request.lastName() == null) {
            throw new UserOperationException(
                    UserOperationException.Reason.INVALID_INPUT,
                    "No fields to update"
            );
        }

        if (request.email() != null) {

            String email = request.email()
                    .trim()
                    .toLowerCase(Locale.ROOT);

            if (email.isBlank()) {
                throw new UserOperationException(
                        UserOperationException.Reason.INVALID_INPUT,
                        "Email must not be blank"
                );
            }

            if (!email.equals(user.getEmail())
                    && userRepository.existsByEmail(email)) {
                throw new UserOperationException(
                        UserOperationException.Reason.CONFLICT,
                        "Email already exists"
                );
            }

            user.changeEmail(email);
        }

        if (request.role() != null) {

            if (user.getRole() == Role.ADMIN
                    && request.role() == Role.USER) {

                if (id.equals(actingAdminId)) {
                    throw new AccessDeniedException(
                            "Cannot demote yourself"
                    );
                }

                if (lockedAdmins.size() <= 1) {
                    throw new UserOperationException(
                            UserOperationException.Reason.CONFLICT,
                            "Cannot demote the last administrator"
                    );
                }
            }

            user.changeRole(request.role());
        }
        if (request.username() != null
                || request.phone() != null
                || request.firstName() != null
                || request.lastName() != null) {

            String username = request.username() == null
                    ? user.getUsername()
                    : request.username().toLowerCase(Locale.ROOT);

            if (!username.equals(user.getUsername())
                    && userRepository.existsByUsername(username)) {
                throw new UsernameAlreadyRegisteredException();
            }

            String phone = request.phone() == null
                    ? user.getPhone()
                    : (request.phone().isEmpty() ? null : request.phone());

            String firstName = request.firstName() == null
                    ? user.getFirstName()
                    : request.firstName().trim();

            String lastName = request.lastName() == null
                    ? user.getLastName()
                    : request.lastName().trim();

            user.changeProfile(username, phone, firstName, lastName);
        }

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new UserOperationException(
                    UserOperationException.Reason.CONFLICT,
                    "Email or username already exists",
                    ex
            );
        }

        return AdminUserResponse.from(user);
    }

    @Transactional
    public void resetPassword(
            Long id,
            ResetAdminPasswordRequest request
    ) {
        AppUser user = findUser(id);

        if (request.newPassword()
                .getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new UserOperationException(
                    UserOperationException.Reason.INVALID_INPUT,
                    "Password exceeds 72 UTF-8 bytes"
            );
        }

        String passwordHash = passwordEncoder.encode(
                request.newPassword()
        );

        user.changePasswordHash(passwordHash);
        user.requirePasswordChange();

        userRepository.saveAndFlush(user);
    }

    private AppUser findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserOperationException(
                        UserOperationException.Reason.USER_NOT_FOUND,
                        "User not found: " + id
                ));
    }
}