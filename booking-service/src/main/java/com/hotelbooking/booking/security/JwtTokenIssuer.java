package com.hotelbooking.booking.security;

import com.hotelbooking.booking.entity.UserRole;
import com.hotelbooking.common.security.JwtProperties;
import com.hotelbooking.common.security.Roles;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenIssuer {

    private static final String SERVICE_SUBJECT = "booking-service";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public String issueUserToken(Long userId, String username, UserRole role) {
        return encode(username, role.name(), Map.of(Roles.USER_ID_CLAIM, userId));
    }

    public String issueServiceToken() {
        return encode(SERVICE_SUBJECT, Roles.INTERNAL, Map.of());
    }

    public Duration tokenTtl() {
        return properties.ttl();
    }

    private String encode(String subject, String role, Map<String, Object> extraClaims) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.ttl()))
                .claim(Roles.ROLES_CLAIM, List.of(role))
                .claims(existing -> existing.putAll(extraClaims))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
