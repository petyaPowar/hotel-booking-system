package com.hotelbooking.booking.security;

import com.hotelbooking.common.security.Roles;
import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

public record CurrentUser(Long id, boolean admin) {

    public static CurrentUser from(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(Roles.ROLES_CLAIM);
        return new CurrentUser(userId(jwt), roles != null && roles.contains(Roles.ADMIN));
    }

    public boolean canAccess(Long ownerId) {
        return admin || id.equals(ownerId);
    }

    private static Long userId(Jwt jwt) {
        if (jwt.getClaims().get(Roles.USER_ID_CLAIM) instanceof Number userId) {
            return userId.longValue();
        }
        throw new InvalidBearerTokenException("Token does not contain a numeric user id");
    }
}
