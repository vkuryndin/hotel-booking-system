package com.hotelbooking.booking.exception;

import com.hotelbooking.booking.config.TraceIdFilter;
import com.hotelbooking.booking.config.UserTokenStateWebConfig;
import com.hotelbooking.booking.repository.AppUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class BookingErrorHandlingTest {

    private static final String TRACE = "b14a7a28-0049-4933-8569-d82719ca7111";
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // No running services, Eureka, real network or real JWT required.
        mvc = standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    void businessErrorHasPreservedStatusCodeAndTraceId() throws Exception {
        mvc.perform(get("/__stage22/not-found").header("X-Trace-Id", TRACE))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Trace-Id", TRACE))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/__stage22/not-found"))
                .andExpect(jsonPath("$.traceId").value(TRACE));
    }

    @Test
    void existingConflictCodeIsPreserved() throws Exception {
        mvc.perform(get("/__stage22/in-progress").header("X-Trace-Id", TRACE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_IN_PROGRESS"))
                .andExpect(jsonPath("$.traceId").value(TRACE));
    }

    @Test
    void validationErrorsKeepFieldErrors() throws Exception {
        mvc.perform(post("/__stage22/input")
                        .header("X-Trace-Id", TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.traceId").value(TRACE));
    }

    @Test
    void malformedJsonHasOurStandardErrorFormat() throws Exception {
        mvc.perform(post("/__stage22/input")
                        .header("X-Trace-Id", TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
                .andExpect(jsonPath("$.traceId").value(TRACE));
    }

    @Test
    void missingHeaderAndUnsupportedMethodAreMapped() throws Exception {
        mvc.perform(get("/__stage22/header").header("X-Trace-Id", TRACE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUEST_HEADER"))
                .andExpect(jsonPath("$.traceId").value(TRACE));

        mvc.perform(get("/__stage22/input").header("X-Trace-Id", TRACE))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void responseStatusExceptionKeepsItsHttpStatus() throws Exception {
        mvc.perform(get("/__stage22/status").header("X-Trace-Id", TRACE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("User already exists"))
                .andExpect(jsonPath("$.traceId").value(TRACE));
    }

    @Test
    void accessDeniedIsStill403() throws Exception {
        mvc.perform(get("/__stage22/access").header("X-Trace-Id", TRACE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.traceId").value(TRACE));
    }

    @Test
    void unexpectedErrorIs500ButItsDetailsAreNotLeaked() throws Exception {
        String body = mvc.perform(get("/__stage22/crash")
                        .header("X-Trace-Id", TRACE))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.traceId").value(TRACE))
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("private database password"));
    }

    @Test
    void gatewayInterceptorRejectionAlsoCarriesTraceId() throws Exception {
        UserTokenStateWebConfig interceptor = new UserTokenStateWebConfig(
                mock(AppUserRepository.class), "secret-for-test");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bookings/my");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put("traceId", TRACE);
        try {
            assertFalse(interceptor.preHandle(request, response, new Object()));
            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains("\"traceId\":\"" + TRACE + "\""));
        } finally {
            MDC.remove("traceId");
        }
    }

    @RestController
    public static class ProbeController {
        public record Input(@NotBlank String name) {}

        @GetMapping("/__stage22/not-found")
        public String notFound() { throw new BookingNotFoundException(999L); }

        @GetMapping("/__stage22/in-progress")
        public String inProgress() { throw new RequestInProgressException(); }

        @PostMapping(value = "/__stage22/input", consumes = MediaType.APPLICATION_JSON_VALUE)
        public String input(@Valid @RequestBody Input input) { return "ok"; }

        @GetMapping("/__stage22/header")
        public String header(@RequestHeader("Required-Header") String header) { return header; }

        @GetMapping("/__stage22/status")
        public String status() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists");
        }

        @GetMapping("/__stage22/access")
        public String access() { throw new AccessDeniedException("denied"); }

        @GetMapping("/__stage22/crash")
        public String crash() { throw new IllegalStateException("private database password"); }
    }
}
