package com.hotelbooking.gateway;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

final class TestTokens {

    static final String SECRET = "gateway-test-secret-key-0123456789-abcdefgh";
    static final String ISSUER = "hotel-booking";

    private TestTokens() {}

    static String valid() {
        Instant now = Instant.now();
        return sign(SECRET, ISSUER, now, now.plusSeconds(3600));
    }

    static String expired() {
        Instant now = Instant.now();
        return sign(SECRET, ISSUER, now.minusSeconds(7200), now.minusSeconds(3600));
    }

    static String signedWithForeignSecret() {
        Instant now = Instant.now();
        return sign("foreign-secret-key-0123456789-abcdefghijklmn", ISSUER, now, now.plusSeconds(3600));
    }

    static String withForeignIssuer() {
        Instant now = Instant.now();
        return sign(SECRET, "someone-else", now, now.plusSeconds(3600));
    }

    static String withoutExpiry() {
        return sign(SECRET, claims(ISSUER, Instant.now()).build());
    }

    private static String sign(String secret, String issuer, Instant issuedAt, Instant expiresAt) {
        return sign(secret, claims(issuer, issuedAt).expiresAt(expiresAt).build());
    }

    private static JwtClaimsSet.Builder claims(String issuer, Instant issuedAt) {
        return JwtClaimsSet.builder()
                .subject("user")
                .issuer(issuer)
                .issuedAt(issuedAt)
                .claim("uid", 2)
                .claim("roles", List.of("USER"));
    }

    private static String sign(String secret, JwtClaimsSet claims) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
