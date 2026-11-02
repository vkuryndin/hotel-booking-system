package com.hotelbooking.booking.service;

import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.exception.UsernameAlreadyRegisteredException;
import com.hotelbooking.booking.repository.AppUserRepository;
import java.util.Locale;

/** Shared preparation of editable profile fields for self-service and admin updates. */
final class ProfileFields {
  private ProfileFields() {}

  static void apply(
      AppUser user,
      AppUserRepository userRepository,
      String requestedUsername,
      String requestedPhone,
      String requestedFirstName,
      String requestedLastName) {
    String username =
        requestedUsername == null ? user.getUsername() : requestedUsername.toLowerCase(Locale.ROOT);

    if (!username.equals(user.getUsername()) && userRepository.existsByUsername(username)) {
      throw new UsernameAlreadyRegisteredException();
    }

    String phone =
        requestedPhone == null
            ? user.getPhone()
            : (requestedPhone.isEmpty() ? null : requestedPhone);
    String firstName = requestedFirstName == null ? user.getFirstName() : requestedFirstName.trim();
    String lastName = requestedLastName == null ? user.getLastName() : requestedLastName.trim();

    user.changeProfile(username, phone, firstName, lastName);
  }
}
