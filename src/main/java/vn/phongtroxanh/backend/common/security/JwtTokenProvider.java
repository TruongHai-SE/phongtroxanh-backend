package vn.phongtroxanh.backend.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-expiration-ms:900000}") long accessExpirationMs,
            @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(UUID userId, String role, String email) {
        return generateAccessToken(userId, role, email, 0);
    }

    public String generateAccessToken(UUID userId, String role, String email, long credentialVersion) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessExpirationMs);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .claim("email", email)
                .claim("type", "ACCESS")
                .claim("credentialVersion", credentialVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(UUID userId, String sessionId) {
        return generateRefreshToken(userId, sessionId, 0);
    }

    public String generateRefreshToken(UUID userId, String sessionId, long credentialVersion) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(refreshExpirationMs);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("sessionId", sessionId)
                .claim("type", "REFRESH")
                .claim("credentialVersion", credentialVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public String generateTempToken(String identifier, String purpose) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(900); // 15 mins

        return Jwts.builder()
                .subject(identifier)
                .id(UUID.randomUUID().toString())
                .claim("purpose", purpose)
                .claim("type", "TEMP")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = extractClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
            return false;
        }
    }

    public UUID extractUserId(String token) {
        Claims claims = extractClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    public String extractRole(String token) {
        Claims claims = extractClaims(token);
        return claims.get("role", String.class);
    }

    public String extractEmail(String token) {
        Claims claims = extractClaims(token);
        return claims.get("email", String.class);
    }

    public String extractSessionId(String token) {
        Claims claims = extractClaims(token);
        return claims.get("sessionId", String.class);
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }
}
