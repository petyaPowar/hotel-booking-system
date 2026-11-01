package com.hotelbooking.booking.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.hotel-client")
public record HotelClientProperties(
        @NotBlank String baseUrl, @NotNull Duration connectTimeout, @NotNull Duration readTimeout) {}
