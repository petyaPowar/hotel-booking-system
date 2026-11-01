package com.hotelbooking.gateway;

import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.jwt.secret=" + TestTokens.SECRET, "app.jwt.issuer=" + TestTokens.ISSUER})
@ActiveProfiles("test")
class ApiGatewayErrorHandlingTest {

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void unreachableBookingService(DynamicPropertyRegistry registry) throws IOException {
        String closedPortUrl = "http://localhost:" + freePort();
        registry.add("spring.cloud.discovery.client.simple.instances.booking-service[0].uri", () -> closedPortUrl);
    }

    @Test
    void hotelRoute_returns503WithErrorBody_whenNoInstanceRegistered() {
        String token = TestTokens.valid();

        WebTestClient.ResponseSpec response = webTestClient
                .get()
                .uri("/api/hotels")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();

        response.expectStatus()
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(503)
                .jsonPath("$.error")
                .isEqualTo("Service Unavailable")
                .jsonPath("$.message")
                .isEqualTo("Upstream service unavailable")
                .jsonPath("$.path")
                .isEqualTo("/api/hotels")
                .jsonPath("$.timestamp")
                .isNotEmpty()
                .jsonPath("$.traceId")
                .isNotEmpty();
    }

    @Test
    void bookingRoute_returns503WithErrorBody_whenConnectionRefused() {
        String token = TestTokens.valid();

        WebTestClient.ResponseSpec response = webTestClient
                .get()
                .uri("/api/bookings/bookings")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();

        response.expectStatus()
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(503)
                .jsonPath("$.message")
                .isEqualTo("Service Unavailable")
                .jsonPath("$.path")
                .isEqualTo("/api/bookings/bookings")
                .jsonPath("$.traceId")
                .isNotEmpty();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
