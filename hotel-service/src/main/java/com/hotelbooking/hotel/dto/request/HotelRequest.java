package com.hotelbooking.hotel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HotelRequest(

        @NotBlank(message = "Hotel name is required")
        @Size(max = 150)
        String name,

        @NotBlank(message = "Hotel address is required")
        @Size(max = 255)
        String address

) {}