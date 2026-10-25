package com.clearing.gateway.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

/**
 * Handles username/password login and issues a signed HS256 JWT.
 *
 * <p>This is a simple credential store for local/dev use. In production,
 * swap the hardcoded credential map for a proper UserDetailsService backed
 * by the database or an external identity provider.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private static final long TOKEN_EXPIRY_HOURS = 8;

    // In-memory credential map — replace with DB lookup when productionising auth.
    private static final Map<String, String> KNOWN_USERS = Map.of(
        "admin",       "admin123",
        "trader",      "trader123",
        "ops",         "ops123"
    );

    @Value("${spring.security.oauth2.resourceserver.jwt.secret}")
    private String jwtSecret;

    /**
     * POST /api/auth/login
     * Body: { "username": "admin", "password": "admin123" }
     * Returns: { "token": "<jwt>" } on success, 401 on bad credentials.
     */
    @PostMapping("/login")
    public Mono<ResponseEntity<Object>> login(@RequestBody LoginRequest loginRequest) {
        String username = loginRequest.username();
        String expectedPassword = KNOWN_USERS.get(username);

        if (expectedPassword == null || !expectedPassword.equals(loginRequest.password())) {
            logger.warn("Failed login attempt for username='{}'", username);
            return Mono.just(ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid username or password")));
        }

        String token = buildJwtToken(username);
        logger.info("Successful login for username='{}'", username);

        return Mono.just(ResponseEntity.ok(Map.of("token", token)));
    }

    private String buildJwtToken(String username) {
        SecretKey signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(username)
                .claim("roles", "PARTICIPANT")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TOKEN_EXPIRY_HOURS, ChronoUnit.HOURS)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Immutable request body for the login endpoint.
     */
    public record LoginRequest(String username, String password) {}
}
