package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.request.ResetAdminPasswordRequest;
import com.hotelbooking.booking.dto.request.UpdateAdminUserRequest;
import com.hotelbooking.booking.dto.response.AdminUserResponse;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;
import com.hotelbooking.booking.exception.UserOperationException;
import com.hotelbooking.booking.repository.AppUserRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserUpdateService {

  private final AppUserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public AdminUserUpdateService(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public AdminUserResponse updateUser(Long id, UpdateAdminUserRequest request, Long actingAdminId) {
    List<AppUser> lockedAdmins =
        request.role() == Role.USER ? userRepository.lockUsersByRole(Role.ADMIN) : List.of();

    AppUser user = findUser(id);

    if (request.email() == null
        && request.role() == null
        && request.username() == null
        && request.phone() == null
        && request.firstName() == null
        && request.lastName() == null) {
      throw new UserOperationException(
          UserOperationException.Reason.INVALID_INPUT, "No fields to update");
    }

    if (request.email() != null) {

      String email = request.email().trim().toLowerCase(Locale.ROOT);

      if (email.isBlank()) {
        throw new UserOperationException(
            UserOperationException.Reason.INVALID_INPUT, "Email must not be blank");
      }

      if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
        throw new UserOperationException(
            UserOperationException.Reason.CONFLICT, "Email already exists");
      }

      user.changeEmail(email);
    }

    if (request.role() != null) {

      if (user.getRole() == Role.ADMIN && request.role() == Role.USER) {

        if (id.equals(actingAdminId)) {
          throw new AccessDeniedException("Cannot demote yourself");
        }

        if (lockedAdmins.size() <= 1) {
          throw new UserOperationException(
              UserOperationException.Reason.CONFLICT, "Cannot demote the last administrator");
        }
      }

      user.changeRole(request.role());
    }
    if (request.username() != null
        || request.phone() != null
        || request.firstName() != null
        || request.lastName() != null) {

      ProfileFields.apply(
          user,
          userRepository,
          request.username(),
          request.phone(),
          request.firstName(),
          request.lastName());
    }

    try {
      userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException ex) {
      throw new UserOperationException(
          UserOperationException.Reason.CONFLICT, "Email or username already exists", ex);
    }

    return AdminUserResponse.from(user);
  }

  @Transactional
  public void resetPassword(Long id, ResetAdminPasswordRequest request) {
    AppUser user = findUser(id);

    if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new UserOperationException(
          UserOperationException.Reason.INVALID_INPUT, "Password exceeds 72 UTF-8 bytes");
    }

    String passwordHash = passwordEncoder.encode(request.newPassword());

    user.changePasswordHash(passwordHash);
    user.requirePasswordChange();

    userRepository.saveAndFlush(user);
  }

  private AppUser findUser(Long id) {
    return userRepository
        .findById(id)
        .orElseThrow(
            () ->
                new UserOperationException(
                    UserOperationException.Reason.USER_NOT_FOUND, "User not found: " + id));
  }
}
