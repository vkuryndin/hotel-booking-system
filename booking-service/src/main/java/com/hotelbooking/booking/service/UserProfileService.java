package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.request.UpdateMyProfileRequest;
import com.hotelbooking.booking.dto.response.UserResponse;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.exception.UsernameAlreadyRegisteredException;
import com.hotelbooking.booking.mapper.AppUserMapper;
import com.hotelbooking.booking.repository.AppUserRepository;

import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hotelbooking.booking.exception.UserOperationException;

import java.util.Locale;

@Service
public class UserProfileService {

    private final AppUserRepository userRepository;
    private final AppUserMapper userMapper;

    public UserProfileService(
            AppUserRepository userRepository,
            AppUserMapper userMapper
    ) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public UserResponse getMyProfile(Long userId) {
        return userMapper.toResponse(findUser(userId));
    }

    @Transactional
    public UserResponse updateMyProfile(
            Long userId,
            UpdateMyProfileRequest request
    ) {
        if (request.username() == null
                && request.phone() == null
                && request.firstName() == null
                && request.lastName() == null) {
            throw new IllegalArgumentException("No fields to update");
        }

        AppUser user = findUser(userId);

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

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new UsernameAlreadyRegisteredException();
        }

        return userMapper.toResponse(user);
    }

    private AppUser findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserOperationException(
                        UserOperationException.Reason.USER_NOT_AUTHENTICATED,
                        "User not found"
                ));
    }
}