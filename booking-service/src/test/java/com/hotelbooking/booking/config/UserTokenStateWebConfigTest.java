package com.hotelbooking.booking.config;

import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;
import com.hotelbooking.booking.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Protects gateway-only access, immediate token revocation and forced password change. */
class UserTokenStateWebConfigTest {
    private AppUserRepository users;
    private UserTokenStateWebConfig filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private AppUser user;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        filter = new UserTokenStateWebConfig(users, "test-gateway-secret");
        request = new MockHttpServletRequest("GET", "/api/bookings/my");
        request.addHeader("X-Gateway-Internal-Secret", "test-gateway-secret");
        response = new MockHttpServletResponse();
        user = new AppUser("test@example.com", "hash", Role.USER);
        when(users.findById(12L)).thenReturn(Optional.of(user));
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private void auth(String subject, String identity, long version, String role) {
        Jwt jwt = Jwt.withTokenValue("unit-test-token")
                .header("alg", "RS256")
                .subject(subject)
                .claim("role", role)
                .claim("tokenIdentity", identity)
                .claim("tokenVersion", version)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private void validAuth() {
        auth("12", user.getTokenIdentity(), user.getTokenVersion(), "USER");
    }

    @Test void missingGatewaySecretIsRejectedBeforeUserLookup() throws Exception {
        request.removeHeader("X-Gateway-Internal-Secret");
        validAuth();
        assertFalse(filter.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("GATEWAY_REQUIRED"));
        verify(users, never()).findById(anyLong());
    }

    @Test void validCurrentJwtPasses() throws Exception {
        validAuth();
        assertTrue(filter.preHandle(request, response, new Object()));
        verify(users).findById(12L);
    }

    @Test void missingUserRevokesJwt() throws Exception {
        validAuth();
        when(users.findById(12L)).thenReturn(Optional.empty());
        assertFalse(filter.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test void roleOrPasswordChangeInvalidatesOldTokenVersion() throws Exception {
        validAuth();
        user.changeRole(Role.ADMIN);
        assertFalse(filter.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("TOKEN_REVOKED"));
    }

    @Test void changedIdentityInvalidatesToken() throws Exception {
        auth("12", "old-identity", user.getTokenVersion(), "USER");
        assertFalse(filter.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test void forcedPasswordChangeBlocksEverythingExceptChangePassword() throws Exception {
        user.requirePasswordChange();
        validAuth();
        assertFalse(filter.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("PASSWORD_CHANGE_REQUIRED"));

        MockHttpServletRequest change = new MockHttpServletRequest(
                "POST", "/api/users/me/change-password");
        change.addHeader("X-Gateway-Internal-Secret", "test-gateway-secret");
        assertTrue(filter.preHandle(change, new MockHttpServletResponse(), new Object()));
    }

    @Test void serviceTokenBypassesUserDatabaseLookup() throws Exception {
        auth("service", "unused", 0, "SERVICE");
        assertTrue(filter.preHandle(request, response, new Object()));
        verify(users, never()).findById(anyLong());
    }

    @Test void nonNumericSubjectIsRejected() throws Exception {
        auth("not-a-number", "unused", 0, "USER");
        assertFalse(filter.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }
}
