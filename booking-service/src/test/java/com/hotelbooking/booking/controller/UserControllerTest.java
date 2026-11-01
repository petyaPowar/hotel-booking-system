package com.hotelbooking.booking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelbooking.booking.IntegrationTestSupport;
import com.hotelbooking.booking.dto.CreateUserRequest;
import com.hotelbooking.booking.dto.UpdateUserRequest;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.entity.User;
import com.hotelbooking.booking.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class UserControllerTest extends IntegrationTestSupport {

    private User admin;
    private User user;

    @BeforeEach
    void createUsers() {
        admin = saveUser("admin", UserRole.ADMIN);
        user = saveUser("user", UserRole.USER);
    }

    @Test
    void createUser_returns201_whenCalledByAdmin() throws Exception {
        CreateUserRequest request = new CreateUserRequest("manager", DEFAULT_PASSWORD, UserRole.ADMIN);

        ResultActions result = mockMvc.perform(post("/user")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));

        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("manager"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void getUsers_returnsPage_whenCalledByAdmin() throws Exception {
        saveUser("alice", UserRole.USER);

        ResultActions result = mockMvc.perform(
                get("/user").param("page", "0").param("size", "2").header(HttpHeaders.AUTHORIZATION, bearer(admin)));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(2));
    }

    @Test
    void getUser_returnsUser_whenExists() throws Exception {
        String path = "/user/" + user.getId();

        ResultActions result = mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(admin)));

        result.andExpect(status().isOk()).andExpect(jsonPath("$.username").value("user"));
    }

    @Test
    void getUser_returns404_whenAbsent() throws Exception {
        String path = "/user/999999";

        ResultActions result = mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(admin)));

        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void patchUser_updatesOnlyProvidedFields() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest(null, null, UserRole.ADMIN);

        ResultActions result = mockMvc.perform(patch("/user/" + user.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPassword())
                .isEqualTo(user.getPassword());
    }

    @Test
    void patchUser_returns409_whenUsernameTaken() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest("admin", null, null);

        ResultActions result = mockMvc.perform(patch("/user/" + user.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));

        result.andExpect(status().isConflict());
    }

    @Test
    void deleteUser_returns204_whenUserHasNoActiveBookings() throws Exception {
        saveBooking(user, 1L, BookingStatus.CANCELLED, "old");

        ResultActions result =
                mockMvc.perform(delete("/user/" + user.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)));

        result.andExpect(status().isNoContent());
        assertThat(userRepository.existsById(user.getId())).isFalse();
    }

    @Test
    void deleteUser_returns409_whenUserHasConfirmedBooking() throws Exception {
        saveBooking(user, 1L, BookingStatus.CONFIRMED, "active");

        ResultActions result =
                mockMvc.perform(delete("/user/" + user.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)));

        result.andExpect(status().isConflict());
        assertThat(userRepository.existsById(user.getId())).isTrue();
    }

    @Test
    void adminEndpoints_return403_whenCalledByUserRole() throws Exception {
        String token = bearer(user);

        ResultActions result = mockMvc.perform(get("/user").header(HttpHeaders.AUTHORIZATION, token));

        result.andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void deleteUser_returns403_whenCalledByUserRole() throws Exception {
        String path = "/user/" + admin.getId();

        ResultActions result = mockMvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, bearer(user)));

        result.andExpect(status().isForbidden());
        assertThat(userRepository.existsById(admin.getId())).isTrue();
    }

    @Test
    void adminEndpoints_return401_withoutToken() throws Exception {
        String path = "/user";

        ResultActions result = mockMvc.perform(get(path));

        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void adminEndpoints_return401_whenTokenInvalid() throws Exception {
        String token = "Bearer invalid.token.value";

        ResultActions result = mockMvc.perform(get("/user").header(HttpHeaders.AUTHORIZATION, token));

        result.andExpect(status().isUnauthorized());
    }
}
