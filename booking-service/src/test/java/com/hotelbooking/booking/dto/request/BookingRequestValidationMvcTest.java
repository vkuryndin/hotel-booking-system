package com.hotelbooking.booking.dto.request;

import com.hotelbooking.booking.config.TraceIdFilter;
import com.hotelbooking.booking.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class BookingRequestValidationMvcTest {
    private static final String TRACE_ID = "b14a7a28-0049-4933-8569-d82719ca7111";
    private MockMvc mvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = standaloneSetup(new ValidationProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new TraceIdFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.destroy();
    }

    private String json(LocalDate in, LocalDate out, int guests) {
        return "{\"roomId\":1,\"checkIn\":\"" + in +
                "\",\"checkOut\":\"" + out +
                "\",\"guestCount\":" + guests + "}";
    }

    @Test
    void tooManyGuestsReturnValidationErrorWithTraceId() throws Exception {
        mvc.perform(post("/__stage26/validate")
                        .header("X-Trace-Id", TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(LocalDate.now().plusDays(4),
                                LocalDate.now().plusDays(5), 21)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.guestCount").exists())
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    void checkoutTodayReturnsValidationError() throws Exception {
        mvc.perform(post("/__stage26/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(LocalDate.now().plusDays(4),
                                LocalDate.now(), 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.checkOut").exists());
    }

    @Test
    void validBoundaryRequestPassesMvcValidation() throws Exception {
        mvc.perform(post("/__stage26/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(LocalDate.now().plusDays(4),
                                LocalDate.now().plusDays(5), 20)))
                .andExpect(status().isOk())
                .andExpect(content().string("accepted"));
    }

    @RestController
    public static class ValidationProbeController {
        @PostMapping("/__stage26/validate")
        public String validate(@Valid @RequestBody BookingRequest request) {
            return "accepted";
        }
    }
}
