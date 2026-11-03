package com.hotelbooking.booking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelbooking.booking.config.PublicAuthRateLimitConfig;
import com.hotelbooking.booking.config.SecurityConfig;
import com.hotelbooking.booking.config.UserTokenStateWebConfig;
import com.hotelbooking.booking.dto.request.BookingRequest;
import com.hotelbooking.booking.dto.request.CreateAdminUserRequest;
import com.hotelbooking.booking.dto.request.LoginRequest;
import com.hotelbooking.booking.dto.request.RegisterRequest;
import com.hotelbooking.booking.dto.response.AdminUserResponse;
import com.hotelbooking.booking.dto.response.BookingResponse;
import com.hotelbooking.booking.dto.response.LoginResponse;
import com.hotelbooking.booking.dto.response.UserResponse;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.entity.Role;
import com.hotelbooking.booking.repository.AppUserRepository;
import com.hotelbooking.booking.security.LoginRegistrationRateLimiter;
import com.hotelbooking.booking.security.SecurityExceptionHandler;
import com.hotelbooking.booking.service.AdminUserReadService;
import com.hotelbooking.booking.service.AdminUserService;
import com.hotelbooking.booking.service.AdminUserUpdateService;
import com.hotelbooking.booking.service.BookingQueryService;
import com.hotelbooking.booking.service.BookingService;
import com.hotelbooking.booking.service.LoginService;
import com.hotelbooking.booking.service.ManualBookingRecoveryService;
import com.hotelbooking.booking.service.RegistrationService;
import com.hotelbooking.booking.service.UserPasswordService;
import com.hotelbooking.booking.service.UserProfileService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@WebMvcTest(
    controllers = {
      AdminUserController.class, AdminUserReadController.class, AdminUserUpdateController.class,
      BookingController.class, BookingRecoveryAdminController.class, LoginController.class,
      RegistrationController.class, UserPasswordController.class, UserProfileController.class
    },
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = PublicAuthRateLimitConfig.class))
@Import({SecurityConfig.class, SecurityExceptionHandler.class, UserTokenStateWebConfig.class})
@TestPropertySource(properties = "GATEWAY_INTERNAL_SECRET=mvc-gateway-secret")
@AutoConfigureMockMvc
@MockitoBean(types = LoginRegistrationRateLimiter.class)
class BookingRoutesMvcTest {
  private static final String USER_JSON =
      """
      {"email":"mvc@example.com","password":"Password123!","username":"mvcuser",
       "firstName":"Mvc","lastName":"User"}
      """;
  private static final String ADMIN_JSON =
      """
      {"email":"mvc@example.com","password":"Password123!","username":"mvcuser",
       "firstName":"Mvc","lastName":"User","role":"USER"}
      """;
  private static final String LOGIN_JSON =
      """
      {"email":"mvc@example.com","password":"Password123!"}
      """;
  private static final String KEY = "aa14aa14-aa14-4a14-8a14-aa14aa14aa14";
  private static final String GATEWAY_SECRET = "mvc-gateway-secret";
  private AppUser activeUser;

  @Autowired private MockMvc mvc;
  @Autowired private RequestMappingHandlerMapping routeMapping;
  @MockitoBean private RegistrationService registrationService;
  @MockitoBean private LoginService loginService;
  @MockitoBean private AdminUserService adminUserService;
  @MockitoBean private AdminUserReadService readService;
  @MockitoBean private AdminUserUpdateService updateService;
  @MockitoBean private UserProfileService profileService;
  @MockitoBean private UserPasswordService passwordService;
  @MockitoBean private BookingService bookingService;
  @MockitoBean private BookingQueryService bookingQueryService;
  @MockitoBean private ManualBookingRecoveryService manualRecovery;
  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private AppUserRepository userRepository;

  record Route(
      String method,
      String path,
      Class<?> controller,
      String handler,
      int expectedStatus,
      String body) {
    @Override
    public String toString() {
      return method + " " + path + " -> " + expectedStatus;
    }
  }

  @BeforeEach
  void authenticationAndResponses() {
    activeUser = new AppUser("mvc@example.com", "hash", Role.USER, "mvcuser", null, "Mvc", "User");
    when(userRepository.findById(42L)).thenReturn(Optional.of(activeUser));
    when(jwtDecoder.decode(anyString()))
        .thenAnswer(
            invocation -> {
              String token = invocation.getArgument(0);
              if ("mvc-user".equals(token) || "mvc-admin".equals(token)) {
                return jwt(token, "mvc-admin".equals(token) ? "ADMIN" : "USER");
              }
              if ("mvc-service".equals(token)) {
                return Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .subject("booking-service")
                    .claim("role", "SERVICE")
                    .audience(List.of("booking-service"))
                    .build();
              }
              throw new BadJwtException("Test token is invalid");
            });
    UserResponse user =
        new UserResponse(7L, "mvc@example.com", "mvcuser", null, "Mvc", "User", Role.USER);
    AdminUserResponse adminUser =
        new AdminUserResponse(7L, "mvc@example.com", "mvcuser", null, "Mvc", "User", Role.USER);
    when(registrationService.register(any(RegisterRequest.class))).thenReturn(user);
    when(loginService.login(any(LoginRequest.class)))
        .thenReturn(
            new LoginResponse(
                "test-jwt", "Bearer", 3600, false, "mvcuser", "Mvc", "User", "mvc@example.com"));
    when(adminUserService.createUser(any(CreateAdminUserRequest.class))).thenReturn(adminUser);
    when(bookingService.createBooking(anyLong(), any(BookingRequest.class), any(UUID.class)))
        .thenReturn(
            new BookingResponse(
                12L,
                42L,
                1L,
                LocalDate.of(2107, 1, 1),
                LocalDate.of(2107, 1, 2),
                BookingStatus.CONFIRMED,
                Instant.now(),
                1));
    when(readService.getAllUsers(
            any(Pageable.class), org.mockito.ArgumentMatchers.nullable(String.class)))
        .thenReturn(Page.empty());
    when(bookingQueryService.getAllBookings(
            org.mockito.ArgumentMatchers.nullable(BookingStatus.class), any(Pageable.class)))
        .thenReturn(Page.empty());
    when(bookingQueryService.getMyBookings(
            anyLong(),
            org.mockito.ArgumentMatchers.nullable(BookingStatus.class),
            org.mockito.ArgumentMatchers.nullable(LocalDate.class),
            any(Pageable.class)))
        .thenReturn(Page.empty());
  }

  static Stream<Route> routes() {
    return Stream.of(
        new Route(
            "POST",
            "/api/users/register",
            RegistrationController.class,
            "register",
            201,
            USER_JSON),
        new Route("POST", "/api/users/login", LoginController.class, "login", 200, LOGIN_JSON),
        new Route("POST", "/api/users", AdminUserController.class, "createUser", 201, ADMIN_JSON),
        new Route(
            "GET",
            "/api/users?page=0&size=5&q=mvcuser",
            AdminUserReadController.class,
            "getAllUsers",
            200,
            null),
        new Route("GET", "/api/users/7", AdminUserReadController.class, "getUserById", 200, null),
        new Route(
            "PATCH", "/api/users/7", AdminUserUpdateController.class, "updateUser", 200, "{}"),
        new Route(
            "POST",
            "/api/users/7/reset-password",
            AdminUserUpdateController.class,
            "resetPassword",
            200,
            "{\"newPassword\":\"Password123!\"}"),
        new Route("DELETE", "/api/users/7", AdminUserController.class, "deleteUser", 200, null),
        new Route("GET", "/api/users/me", UserProfileController.class, "getMyProfile", 200, null),
        new Route(
            "PATCH", "/api/users/me", UserProfileController.class, "updateMyProfile", 200, "{}"),
        new Route(
            "POST",
            "/api/users/me/change-password",
            UserPasswordController.class,
            "changePassword",
            200,
            "{\"currentPassword\":\"Password123!\",\"newPassword\":\"NewPassword123!\"}"),
        new Route(
            "GET",
            "/api/users/token-status",
            UserPasswordController.class,
            "tokenStatus",
            204,
            null),
        new Route(
            "POST",
            "/api/bookings",
            BookingController.class,
            "createBooking",
            201,
            "{\"roomId\":1,\"checkIn\":\"2107-01-01\","
                + "\"checkOut\":\"2107-01-02\",\"guestCount\":1}"),
        new Route(
            "GET",
            "/api/bookings/my?page=0&size=10",
            BookingController.class,
            "getMyBookings",
            200,
            null),
        new Route("GET", "/api/bookings/12", BookingController.class, "getBookingById", 200, null),
        new Route(
            "PATCH",
            "/api/bookings/12/cancel",
            BookingController.class,
            "cancelBooking",
            200,
            null),
        new Route(
            "GET",
            "/api/bookings?page=0&size=5",
            BookingController.class,
            "getAllBookings",
            200,
            null),
        new Route(
            "GET",
            "/api/bookings/recovery/exhausted",
            BookingRecoveryAdminController.class,
            "exhausted",
            200,
            null),
        new Route(
            "POST",
            "/api/bookings/12/recovery/retry",
            BookingRecoveryAdminController.class,
            "retry",
            202,
            null));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("routes")
  void everyRouteIsDispatchedToTheCorrectController(Route route) throws Exception {
    mvc.perform(
            requestFor(
                route,
                isPublic(route) ? null : isAdminOnly(route) ? "mvc-admin" : "mvc-user",
                true))
        .andExpect(handler().handlerType(route.controller()))
        .andExpect(handler().methodName(route.handler()))
        .andExpect(status().is(route.expectedStatus()));
  }

  private Jwt jwt(String token, String role) {
    return Jwt.withTokenValue(token)
        .header("alg", "RS256")
        .subject("42")
        .claim("role", role)
        .claim("tokenIdentity", activeUser.getTokenIdentity())
        .claim("tokenVersion", activeUser.getTokenVersion())
        .build();
  }

  private static boolean isPublic(Route route) {
    return route.controller() == RegistrationController.class
        || route.controller() == LoginController.class;
  }

  private static boolean isAdminOnly(Route route) {
    return route.controller() == AdminUserController.class
        || route.controller() == AdminUserReadController.class
        || route.controller() == AdminUserUpdateController.class
        || route.controller() == BookingRecoveryAdminController.class
        || (route.controller() == BookingController.class
            && route.handler().equals("getAllBookings"));
  }

  private static MockHttpServletRequestBuilder requestFor(
      Route route, String token, boolean viaGateway) {
    MockHttpServletRequestBuilder request =
        request(HttpMethod.valueOf(route.method()), route.path().split("\\?", 2)[0])
            .header("Idempotency-Key", KEY);
    addParameters(request, route.path());
    if (route.body() != null) {
      request.contentType(MediaType.APPLICATION_JSON).content(route.body());
    }
    return authorize(request, token, viaGateway);
  }

  private static MockHttpServletRequestBuilder authorize(
      MockHttpServletRequestBuilder request, String token, boolean viaGateway) {
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    if (viaGateway) {
      request.header("X-Gateway-Internal-Secret", GATEWAY_SECRET);
    }
    return request;
  }

  @ParameterizedTest(name = "Anonymous access: {0}")
  @MethodSource("routes")
  void protectedRoutesRejectRequestsWithoutJwt(Route route) throws Exception {
    if (isPublic(route)) {
      return;
    }
    mvc.perform(requestFor(route, null, true))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @ParameterizedTest(name = "USER access: {0}")
  @MethodSource("routes")
  void usersCannotInvokeAdminRoutes(Route route) throws Exception {
    if (!isAdminOnly(route)) {
      return;
    }
    mvc.perform(requestFor(route, "mvc-user", true))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @ParameterizedTest(name = "SERVICE cannot manage ADMIN API: {0}")
  @MethodSource("routes")
  void serviceRoleCannotInvokeAdminRoutes(Route route) throws Exception {
    if (!isAdminOnly(route)) {
      return;
    }
    mvc.perform(requestFor(route, "mvc-service", true))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void adminRoleCanReadUserProfile() throws Exception {
    mvc.perform(authorize(request(HttpMethod.GET, "/api/users/me"), "mvc-admin", true))
        .andExpect(status().isOk());
  }

  @Test
  void serviceRoleCannotReadUserProfile() throws Exception {
    mvc.perform(authorize(request(HttpMethod.GET, "/api/users/me"), "mvc-service", true))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void invalidBearerTokenIsRejectedBeforeController() throws Exception {
    mvc.perform(authorize(request(HttpMethod.GET, "/api/users/me"), "invalid", true))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void missingGatewayHeaderIsRejectedEvenWithValidJwt() throws Exception {
    mvc.perform(authorize(request(HttpMethod.GET, "/api/users/me"), "mvc-user", false))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("GATEWAY_REQUIRED"));
  }

  @Test
  void revokedAccountIsRejectedBeforeController() throws Exception {
    when(userRepository.findById(42L)).thenReturn(Optional.empty());
    mvc.perform(authorize(request(HttpMethod.GET, "/api/users/me"), "mvc-user", true))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  @Test
  void passwordChangeRequirementBlocksOtherEndpoints() throws Exception {
    activeUser.requirePasswordChange();
    when(jwtDecoder.decode("mvc-user")).thenReturn(jwt("mvc-user", "USER"));
    mvc.perform(authorize(request(HttpMethod.GET, "/api/users/me"), "mvc-user", true))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
  }

  @Test
  void passwordChangeEndpointRemainsAccessibleWhenChangeIsRequired() throws Exception {
    activeUser.requirePasswordChange();
    when(jwtDecoder.decode("mvc-user")).thenReturn(jwt("mvc-user", "USER"));
    mvc.perform(
            authorize(
                request(HttpMethod.POST, "/api/users/me/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"currentPassword\":\"Password123!\",\"newPassword\":\"NewPassword123!\"}"),
                "mvc-user",
                true))
        .andExpect(status().isOk());
  }

  @Test
  void publicLoginWorksWithoutJwtButNotWithoutGateway() throws Exception {
    mvc.perform(
            request(HttpMethod.POST, "/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN_JSON))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("GATEWAY_REQUIRED"));
  }

  private static void addParameters(MockHttpServletRequestBuilder request, String path) {
    int separator = path.indexOf('?');
    if (separator == -1) {
      return;
    }
    for (String entry : path.substring(separator + 1).split("&")) {
      String[] pair = entry.split("=", 2);
      request.param(pair[0], pair.length == 2 ? pair[1] : "");
    }
  }

  @Test
  void loginSerializesAllAccountIdentityFields() throws Exception {
    mvc.perform(
            authorize(
                request(HttpMethod.POST, "/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(LOGIN_JSON),
                null,
                true))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("test-jwt"))
        .andExpect(jsonPath("$.username").value("mvcuser"))
        .andExpect(jsonPath("$.firstName").value("Mvc"))
        .andExpect(jsonPath("$.lastName").value("User"))
        .andExpect(jsonPath("$.email").value("mvc@example.com"));
  }

  @Test
  void userSearchBindsQueryAndPageable() throws Exception {
    mvc.perform(
            authorize(
                request(HttpMethod.GET, "/api/users")
                    .param("page", "2")
                    .param("size", "7")
                    .param("q", "admincheck03"),
                "mvc-admin",
                true))
        .andExpect(status().isOk());
    verify(readService)
        .getAllUsers(
            org.mockito.ArgumentMatchers.argThat(
                (Pageable pageable) ->
                    pageable.getPageNumber() == 2 && pageable.getPageSize() == 7),
            org.mockito.ArgumentMatchers.eq("admincheck03"));
  }

  @Test
  void adminSearchSerializesPageContent() throws Exception {
    mvc.perform(
            authorize(
                request(HttpMethod.GET, "/api/users").param("q", "mvcuser"), "mvc-admin", true))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  @Test
  void missingBookingIdempotencyKeyReturns400() throws Exception {
    mvc.perform(
            authorize(
                request(HttpMethod.POST, "/api/bookings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"roomId\":1,\"checkIn\":\"2107-01-01\",\"checkOut\":\"2107-01-02\"}"),
                "mvc-user",
                true))
        .andExpect(status().isBadRequest());
  }

  @Test
  void incompleteRegistrationReturns400() throws Exception {
    mvc.perform(
            authorize(
                request(HttpMethod.POST, "/api/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"mvc@example.com\",\"password\":\"Password123!\"}"),
                null,
                true))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void malformedBookingStatusReturns400() throws Exception {
    mvc.perform(
            authorize(
                request(HttpMethod.GET, "/api/bookings/my").param("status", "NOT_A_STATUS"),
                "mvc-user",
                true))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unknownBookingReturnsDomain404() throws Exception {
    when(bookingQueryService.getBookingById(12L, 42L, false))
        .thenThrow(new com.hotelbooking.booking.exception.BookingNotFoundException(12L));
    mvc.perform(authorize(request(HttpMethod.GET, "/api/bookings/12"), "mvc-user", true))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
  }

  @Test
  void conflictingRegistrationReturns409() throws Exception {
    when(registrationService.register(any(RegisterRequest.class)))
        .thenThrow(new com.hotelbooking.booking.exception.EmailAlreadyRegisteredException());
    mvc.perform(
            authorize(
                request(HttpMethod.POST, "/api/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(USER_JSON),
                null,
                true))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
  }

  @Test
  void bookingSagaUnavailableReturns503() throws Exception {
    when(bookingService.createBooking(anyLong(), any(BookingRequest.class), any(UUID.class)))
        .thenThrow(
            new com.hotelbooking.booking.exception.BookingSagaUnavailableException(
                "Temporarily unavailable", new IllegalStateException("test failure")));
    mvc.perform(
            authorize(
                request(HttpMethod.POST, "/api/bookings")
                    .header("Idempotency-Key", KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"roomId\":1,\"checkIn\":\"2107-01-01\",\"checkOut\":\"2107-01-02\"}"),
                "mvc-user",
                true))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("BOOKING_SAGA_UNAVAILABLE"));
  }

  @Test
  void everyControllerHandlerHasAnHttpContractCase() {
    Set<String> mapped =
        routeMapping.getHandlerMethods().values().stream()
            .filter(
                handler ->
                    handler
                        .getBeanType()
                        .getPackageName()
                        .equals("com.hotelbooking.booking.controller"))
            .map(
                handler ->
                    handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName())
            .collect(Collectors.toSet());
    Set<String> tested =
        routes()
            .map(route -> route.controller().getSimpleName() + "#" + route.handler())
            .collect(Collectors.toSet());
    org.junit.jupiter.api.Assertions.assertEquals(
        mapped, tested, "Every controller handler must have an HTTP contract case");
  }
}
