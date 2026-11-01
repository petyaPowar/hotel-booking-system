package com.hotelbooking.hotel.support;

import com.hotelbooking.common.security.JwtProperties;
import com.hotelbooking.common.security.Roles;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

public final class JwtTestTokens {

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public JwtTestTokens(SecretKey secretKey, JwtProperties properties) {
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        this.properties = properties;
    }

    public String admin() {
        return bearer("admin", Roles.ADMIN);
    }

    public String user() {
        return bearer("user", Roles.USER);
    }

    public String internal() {
        return bearer("booking-service", Roles.INTERNAL);
    }

    public String userWithoutExpiry() {
        return bearer(claims("user", Roles.USER).build());
    }

    private String bearer(String subject, String role) {
        return bearer(claims(subject, role)
                .expiresAt(Instant.now().plus(properties.ttl()))
                .build());
    }

    private JwtClaimsSet.Builder claims(String subject, String role) {
        return JwtClaimsSet.builder()
                .subject(subject)
                .issuer(properties.issuer())
                .issuedAt(Instant.now())
                .claim(Roles.ROLES_CLAIM, List.of(role));
    }

    private String bearer(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return "Bearer "
                + encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
