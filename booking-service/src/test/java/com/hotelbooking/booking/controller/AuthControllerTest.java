package com.hotelbooking.booking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.hotelbooking.booking.IntegrationTestSupport;
import com.hotelbooking.booking.dto.AuthRequest;
import com.hotelbooking.booking.dto.RegisterRequest;
import com.hotelbooking.booking.entity.User;
import com.hotelbooking.booking.entity.UserRole;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.ResultActions;

class AuthControllerTest extends IntegrationTestSupport {

    private static final int LONG_ASCII_PASSWORD_LENGTH = 80;
    private static final int CYRILLIC_PASSWORD_LENGTH = 40;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void register_returns201WithUserToken_whenUsernameIsFree() throws Exception {
        RegisterRequest request = new RegisterRequest("newuser", DEFAULT_PASSWORD);

        ResultActions result = postJson("/user/register", request);

        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.role").value("USER"));
        User saved = userRepository.findByUsername("newuser").orElseThrow();
        Jwt jwt = jwtDecoder.decode(accessToken(result));
        assertThat(jwt.getSubject()).isEqualTo("newuser");
        assertThat(jwt.<Number>getClaim("uid").longValue()).isEqualTo(saved.getId());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("hotel-booking");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void register_storesBcryptHash_insteadOfPlainPassword() throws Exception {
        RegisterRequest request = new RegisterRequest("hashed", DEFAULT_PASSWORD);

        postJson("/user/register", request);

        String storedPassword =
                userRepository.findByUsername("hashed").orElseThrow().getPassword();
        assertThat(storedPassword).startsWith("$2").isNotEqualTo(DEFAULT_PASSWORD);
    }

    @Test
    void register_returns409_whenUsernameAlreadyTaken() throws Exception {
        saveUser("taken", UserRole.USER);

        ResultActions result = postJson("/user/register", new RegisterRequest("taken", DEFAULT_PASSWORD));

        result.andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void register_returns400_whenPasswordTooShort() throws Exception {
        RegisterRequest request = new RegisterRequest("shortpass", "123");

        ResultActions result = postJson("/user/register", request);

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void register_returns400_whenPasswordLongerThan72Chars() throws Exception {
        RegisterRequest request = new RegisterRequest("longpass", "a".repeat(LONG_ASCII_PASSWORD_LENGTH));

        ResultActions result = postJson("/user/register", request);

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        assertThat(userRepository.findByUsername("longpass")).isEmpty();
    }

    @Test
    void register_returns400_whenPasswordExceeds72BytesInUtf8() throws Exception {
        RegisterRequest request = new RegisterRequest("cyrillic", "я".repeat(CYRILLIC_PASSWORD_LENGTH));

        ResultActions result = postJson("/user/register", request);

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("must not exceed 72 bytes in UTF-8"));
        assertThat(userRepository.findByUsername("cyrillic")).isEmpty();
    }

    @Test
    void auth_returns200WithToken_whenCredentialsValid() throws Exception {
        saveUser("admin", UserRole.ADMIN);

        ResultActions result = postJson("/user/auth", new AuthRequest("admin", DEFAULT_PASSWORD));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        assertThat(jwtDecoder.decode(accessToken(result)).getClaimAsStringList("roles"))
                .containsExactly("ADMIN");
    }

    @Test
    void auth_returns401_whenPasswordWrong() throws Exception {
        saveUser("victim", UserRole.USER);

        ResultActions result = postJson("/user/auth", new AuthRequest("victim", "wrong-password"));

        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void auth_returns401_whenUserUnknown() throws Exception {
        AuthRequest request = new AuthRequest("ghost", DEFAULT_PASSWORD);

        ResultActions result = postJson("/user/auth", request);

        result.andExpect(status().isUnauthorized());
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mockMvc.perform(
                post(path).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private String accessToken(ResultActions result) throws Exception {
        JsonNode body = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }
}
