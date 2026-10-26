package com.cinebook.security;

import com.cinebook.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final AppProperties appProperties;
    private SecretKey secretKey;

    public JwtTokenProvider(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void init() {
        String configuredSecret = appProperties.getJwt().getSecret();
        if (configuredSecret == null || configuredSecret.trim().length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be configured and contain at least 32 characters."
            );
        }
        this.secretKey = Keys.hmacShaKeyFor(
                configuredSecret.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    public String generateToken(UserPrincipal principal) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + appProperties.getJwt().getExpirationMs());

        return Jwts.builder()
                .subject(String.valueOf(principal.getId()))
                .claim("email", principal.getEmail())
                .claim("role", principal.getRole().name())
                .claim("name", principal.getFullName())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }
}
