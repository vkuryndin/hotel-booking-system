package com.hotelbooking.hotel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelbooking.hotel.config.MethodSecurityConfig;
import com.hotelbooking.hotel.config.SecurityConfig;
import com.hotelbooking.hotel.config.SecurityExceptionHandler;
import com.hotelbooking.hotel.config.UserTokenStateWebConfig;
import com.hotelbooking.hotel.dto.request.HotelRequest;
import com.hotelbooking.hotel.dto.request.RoomRequest;
import com.hotelbooking.hotel.dto.response.HotelResponse;
import com.hotelbooking.hotel.dto.response.RoomResponse;
import com.hotelbooking.hotel.entity.RoomReservation;
import com.hotelbooking.hotel.service.HotelPhotoService;
import com.hotelbooking.hotel.service.HotelPresentationService;
import com.hotelbooking.hotel.service.HotelService;
import com.hotelbooking.hotel.service.HotelStatisticsService;
import com.hotelbooking.hotel.service.RoomDetailsService;
import com.hotelbooking.hotel.service.RoomPhotoService;
import com.hotelbooking.hotel.service.RoomReservationService;
import com.hotelbooking.hotel.service.RoomSearchService;
import com.hotelbooking.hotel.service.RoomService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@WebMvcTest(
    controllers = {
      HotelController.class, HotelPhotoController.class, HotelPresentationController.class,
      HotelStatisticsController.class, RoomController.class, RoomDetailsController.class,
      RoomPhotoController.class, RoomReservationController.class
    })
@Import({
  SecurityConfig.class,
  MethodSecurityConfig.class,
  SecurityExceptionHandler.class,
  UserTokenStateWebConfig.class
})
@TestPropertySource(properties = "GATEWAY_INTERNAL_SECRET=mvc-gateway-secret")
@AutoConfigureMockMvc
class HotelRoutesMvcTest {
  private static final String GATEWAY_SECRET = "mvc-gateway-secret";
  private static final String HOTEL_JSON = "{\"name\":\"MVC Hotel\",\"address\":\"Yerevan\"}";
  private static final String ROOM_JSON =
      "{\"hotelId\":1,\"number\":\"101\",\"capacity\":2,\"pricePerNight\":125}";
  private static final String RESERVATION_ID = "bb14bb14-bb14-4b14-8b14-bb14bb14bb14";
  private static final String RESERVE_JSON =
      "{\"reservationId\":\""
          + RESERVATION_ID
          + "\",\"checkIn\":\"2107-01-01\",\"checkOut\":\"2107-01-03\",\"guestCount\":2}";
  private static final String RELEASE_JSON =
      "{\"reservationId\":\"" + RESERVATION_ID + "\",\"compensation\":false}";

  @Autowired private MockMvc mvc;
  @Autowired private RequestMappingHandlerMapping routeMapping;
  @MockitoBean private HotelService hotelService;
  @MockitoBean private HotelPhotoService hotelPhotoService;
  @MockitoBean private HotelPresentationService presentationService;
  @MockitoBean private HotelStatisticsService statisticsService;
  @MockitoBean private RoomService roomService;
  @MockitoBean private RoomSearchService roomSearchService;
  @MockitoBean private RoomDetailsService detailsService;
  @MockitoBean private RoomPhotoService roomPhotoService;
  @MockitoBean private RoomReservationService reservationService;
  @MockitoBean private JwtDecoder jwtDecoder;

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
  void successfulServiceResponses() {
    when(jwtDecoder.decode(anyString()))
        .thenAnswer(
            invocation -> {
              String token = invocation.getArgument(0);
              if ("mvc-user".equals(token) || "mvc-admin".equals(token)) {
                return Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .subject("42")
                    .claim("role", "mvc-admin".equals(token) ? "ADMIN" : "USER")
                    .build();
              }
              if ("mvc-service".equals(token)
                  || "invalid-service".equals(token)
                  || "invalid-audience".equals(token)) {
                return Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .subject(
                        "invalid-service".equals(token) ? "untrusted-service" : "booking-service")
                    .claim("role", "SERVICE")
                    .audience(
                        List.of(
                            "invalid-audience".equals(token) ? "other-service" : "hotel-service"))
                    .build();
              }
              throw new BadJwtException("Test token is invalid");
            });
    when(hotelService.createHotel(any(HotelRequest.class)))
        .thenReturn(new HotelResponse(1L, "MVC Hotel", "Yerevan", null, null, null, null, null));
    when(roomService.createRoom(any(RoomRequest.class)))
        .thenReturn(
            new RoomResponse(2L, 1L, "101", true, 0L, null, 2, new BigDecimal("125"), null));
    when(hotelPhotoService.contentType(anyLong(), anyLong())).thenReturn("image/jpeg");
    when(roomPhotoService.contentType(anyLong(), anyLong())).thenReturn("image/jpeg");
    when(hotelPhotoService.getFile(anyLong(), anyLong()))
        .thenReturn(new ByteArrayResource(new byte[] {1, 2, 3}));
    when(roomPhotoService.getFile(anyLong(), anyLong()))
        .thenReturn(new ByteArrayResource(new byte[] {1, 2, 3}));
    RoomReservation reservation = org.mockito.Mockito.mock(RoomReservation.class);
    when(reservation.getReservationId()).thenReturn(UUID.fromString(RESERVATION_ID));
    when(reservation.getCheckIn()).thenReturn(LocalDate.of(2107, 1, 1));
    when(reservation.getCheckOut()).thenReturn(LocalDate.of(2107, 1, 3));
    when(reservationService.reserve(
            any(UUID.class),
            anyLong(),
            any(LocalDate.class),
            any(LocalDate.class),
            org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(reservation);
    when(reservationService.release(
            any(UUID.class), anyLong(), org.mockito.ArgumentMatchers.anyBoolean()))
        .thenReturn(reservation);
  }

  static Stream<Route> routes() {
    return Stream.of(
        new Route("POST", "/api/hotels", HotelController.class, "createHotel", 201, HOTEL_JSON),
        new Route(
            "GET", "/api/hotels?page=0&size=10", HotelController.class, "getHotels", 200, null),
        new Route("GET", "/api/hotels/1", HotelController.class, "getHotelById", 200, null),
        new Route("PUT", "/api/hotels/1", HotelController.class, "updateHotel", 200, HOTEL_JSON),
        new Route(
            "GET",
            "/api/hotels/1/room-selection-strategy",
            HotelController.class,
            "getRoomSelectionStrategy",
            200,
            null),
        new Route(
            "PUT",
            "/api/hotels/1/room-selection-strategy",
            HotelController.class,
            "updateRoomSelectionStrategy",
            200,
            "{\"strategy\":\"LEAST_BOOKED\"}"),
        new Route("DELETE", "/api/hotels/1", HotelController.class, "deleteHotel", 204, null),
        new Route("GET", "/api/hotels/1/photos", HotelPhotoController.class, "list", 200, null),
        new Route(
            "GET", "/api/hotels/1/photos/4/file", HotelPhotoController.class, "getFile", 200, null),
        new Route("POST", "/api/hotels/1/photos", HotelPhotoController.class, "upload", 201, null),
        new Route(
            "PATCH",
            "/api/hotels/1/photos/4/primary",
            HotelPhotoController.class,
            "setPrimary",
            200,
            null),
        new Route(
            "PATCH",
            "/api/hotels/1/photos/4/order?sortOrder=2",
            HotelPhotoController.class,
            "setOrder",
            200,
            null),
        new Route(
            "DELETE", "/api/hotels/1/photos/4", HotelPhotoController.class, "delete", 204, null),
        new Route(
            "PATCH",
            "/api/hotels/1/presentation",
            HotelPresentationController.class,
            "update",
            200,
            "{\"rating\":4.0}"),
        new Route(
            "GET",
            "/api/hotels/1/statistics",
            HotelStatisticsController.class,
            "getStatistics",
            200,
            null),
        new Route("POST", "/api/rooms", RoomController.class, "createRoom", 201, ROOM_JSON),
        new Route(
            "GET",
            "/api/rooms?hotelId=1&page=0&size=10",
            RoomController.class,
            "getRooms",
            200,
            null),
        new Route("GET", "/api/rooms/2", RoomController.class, "getRoomById", 200, null),
        new Route(
            "PATCH",
            "/api/rooms/2/availability",
            RoomController.class,
            "updateAvailability",
            200,
            "{\"available\":true}"),
        new Route(
            "GET",
            "/api/rooms/available?hotelId=1",
            RoomController.class,
            "getAvailableRooms",
            200,
            null),
        new Route(
            "PUT", "/api/rooms/2", RoomController.class, "updateRoom", 200, "{\"number\":\"102\"}"),
        new Route("DELETE", "/api/rooms/2", RoomController.class, "deleteRoom", 204, null),
        new Route(
            "GET",
            "/api/rooms/recommend?hotelId=1&checkIn=2107-01-01&checkOut=2107-01-03",
            RoomController.class,
            "recommendRooms",
            200,
            null),
        new Route(
            "PATCH",
            "/api/rooms/2/details",
            RoomDetailsController.class,
            "update",
            200,
            "{\"capacity\":2}"),
        new Route("GET", "/api/rooms/2/photos", RoomPhotoController.class, "list", 200, null),
        new Route(
            "GET", "/api/rooms/2/photos/4/file", RoomPhotoController.class, "getFile", 200, null),
        new Route("POST", "/api/rooms/2/photos", RoomPhotoController.class, "upload", 201, null),
        new Route(
            "PATCH",
            "/api/rooms/2/photos/4/primary",
            RoomPhotoController.class,
            "setPrimary",
            200,
            null),
        new Route(
            "PATCH",
            "/api/rooms/2/photos/4/order?sortOrder=2",
            RoomPhotoController.class,
            "setOrder",
            200,
            null),
        new Route(
            "DELETE", "/api/rooms/2/photos/4", RoomPhotoController.class, "delete", 204, null),
        new Route(
            "POST",
            "/api/rooms/2/confirm-availability",
            RoomReservationController.class,
            "confirmAvailability",
            200,
            RESERVE_JSON),
        new Route(
            "POST",
            "/api/rooms/2/release",
            RoomReservationController.class,
            "release",
            200,
            RELEASE_JSON));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("routes")
  void everyRouteIsDispatchedAndReturnsItsDeclaredStatus(Route route) throws Exception {
    AbstractMockHttpServletRequestBuilder<?> request =
        requestFor(
            route,
            isServiceOnly(route) ? "mvc-service" : isAdminOnly(route) ? "mvc-admin" : "mvc-user",
            !isServiceOnly(route));
    mvc.perform(request)
        .andExpect(handler().handlerType(route.controller()))
        .andExpect(handler().methodName(route.handler()))
        .andExpect(status().is(route.expectedStatus()));
  }

  private static boolean isServiceOnly(Route route) {
    return route.controller() == RoomReservationController.class;
  }

  private static boolean isAdminOnly(Route route) {
    if (isServiceOnly(route)) {
      return false;
    }
    String method = route.method();
    return !(method.equals("GET") && !route.path().contains("/statistics"));
  }

  private static AbstractMockHttpServletRequestBuilder<?> requestFor(
      Route route, String token, boolean viaGateway) {
    String path = route.path().split("\\?", 2)[0];
    AbstractMockHttpServletRequestBuilder<?> request;
    if (route.method().equals("POST") && path.contains("/photos") && !path.contains("/file")) {
      request =
          multipart(path)
              .file(
                  new MockMultipartFile("file", "picture.jpg", "image/jpeg", new byte[] {1, 2, 3}));
    } else {
      request = request(HttpMethod.valueOf(route.method()), path);
      addParameters(request, route.path());
      if (route.body() != null) {
        request.contentType(MediaType.APPLICATION_JSON).content(route.body());
      }
    }
    return authorize(request, token, viaGateway);
  }

  private static AbstractMockHttpServletRequestBuilder<?> authorize(
      AbstractMockHttpServletRequestBuilder<?> request, String token, boolean viaGateway) {
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
  void everyProtectedRouteRejectsUnauthenticatedRequests(Route route) throws Exception {
    mvc.perform(requestFor(route, null, false))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @ParameterizedTest(name = "USER access: {0}")
  @MethodSource("routes")
  void usersCannotInvokeAdminOrServiceRoutes(Route route) throws Exception {
    if (!isAdminOnly(route) && !isServiceOnly(route)) {
      return;
    }
    mvc.perform(requestFor(route, "mvc-user", true))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @ParameterizedTest(name = "SERVICE access: {0}")
  @MethodSource("routes")
  void serviceCannotInvokeAdminRoutes(Route route) throws Exception {
    if (!isAdminOnly(route)) {
      return;
    }
    mvc.perform(requestFor(route, "mvc-service", false))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @ParameterizedTest(name = "SERVICE read access: {0}")
  @MethodSource("routes")
  void serviceCanReadCatalogWithoutGatewayHeader(Route route) throws Exception {
    if (isAdminOnly(route) || isServiceOnly(route)) {
      return;
    }
    mvc.perform(requestFor(route, "mvc-service", false))
        .andExpect(status().is(route.expectedStatus()));
  }

  @Test
  void adminCanReadStatistics() throws Exception {
    mvc.perform(authorize(request(HttpMethod.GET, "/api/hotels/1/statistics"), "mvc-admin", true))
        .andExpect(status().isOk());
  }

  @Test
  void userCannotBypassGatewayForHotelRead() throws Exception {
    mvc.perform(authorize(request(HttpMethod.GET, "/api/hotels"), "mvc-user", false))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("GATEWAY_REQUIRED"));
  }

  @Test
  void serviceTokenWithWrongSubjectIsRejected() throws Exception {
    mvc.perform(
            authorize(
                request(HttpMethod.POST, "/api/rooms/2/release")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(RELEASE_JSON),
                "invalid-service",
                false))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INVALID_SERVICE_TOKEN"));
  }

  @Test
  void serviceTokenWithWrongAudienceIsRejected() throws Exception {
    mvc.perform(
            authorize(
                request(HttpMethod.POST, "/api/rooms/2/release")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(RELEASE_JSON),
                "invalid-audience",
                false))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INVALID_SERVICE_TOKEN"));
  }

  @Test
  void administratorCannotUseInternalRoomReservationEndpoint() throws Exception {
    mvc.perform(
            authorize(
                request(HttpMethod.POST, "/api/rooms/2/release")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(RELEASE_JSON),
                "mvc-admin",
                true))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void userCannotReleaseRoomEvenWhenGatewayAuthenticated() throws Exception {
    mvc.perform(
            authorize(
                request(HttpMethod.POST, "/api/rooms/2/release")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(RELEASE_JSON),
                "mvc-user",
                true))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void invalidBearerTokenReturns401() throws Exception {
    mvc.perform(authorize(request(HttpMethod.GET, "/api/hotels"), "invalid", true))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  private static void addParameters(AbstractMockHttpServletRequestBuilder<?> request, String path) {
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
  void invalidHotelIsRejectedByMvcValidation() throws Exception {
    mvc.perform(
            authorize(
                request(HttpMethod.POST, "/api/hotels")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"\",\"address\":\"\"}"),
                "mvc-admin",
                true))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void roomListRequiresHotelId() throws Exception {
    mvc.perform(authorize(request(HttpMethod.GET, "/api/rooms"), "mvc-user", true))
        .andExpect(status().isBadRequest());
  }

  @Test
  void invalidRoomCategoryIsRejectedBeforeServiceInvocation() throws Exception {
    mvc.perform(
            authorize(
                request(HttpMethod.GET, "/api/rooms")
                    .param("hotelId", "1")
                    .param("category", "WRONG_CATEGORY"),
                "mvc-user",
                true))
        .andExpect(status().isBadRequest());
  }

  @Test
  void releaseRequiresReservationId() throws Exception {
    mvc.perform(
            authorize(
                request(HttpMethod.POST, "/api/rooms/2/release")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"compensation\":false}"),
                "mvc-service",
                false))
        .andExpect(status().isBadRequest());
  }

  @Test
  void missingHotelReturnsDomain404() throws Exception {
    when(hotelService.getHotelById(1L))
        .thenThrow(new com.hotelbooking.hotel.exception.HotelNotFoundException(1L));
    mvc.perform(authorize(request(HttpMethod.GET, "/api/hotels/1"), "mvc-user", true))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("HOTEL_NOT_FOUND"));
  }

  @Test
  void conflictingHotelCreationReturns409() throws Exception {
    when(hotelService.createHotel(any(HotelRequest.class)))
        .thenThrow(
            new com.hotelbooking.hotel.exception.HotelAlreadyExistsException(
                "MVC Hotel", "Yerevan"));
    mvc.perform(
            authorize(
                request(HttpMethod.POST, "/api/hotels")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(HOTEL_JSON),
                "mvc-admin",
                true))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("HOTEL_ALREADY_EXISTS"));
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
                        .equals("com.hotelbooking.hotel.controller"))
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
