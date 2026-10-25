package com.clearing.gateway.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(ServerHttpSecurity.CorsSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                // Health checks must be open for Kubernetes probes and Prometheus scraping
                .pathMatchers("/actuator/**").permitAll()
                // Login endpoint is the only unauthenticated business path
                .pathMatchers("/api/auth/login").permitAll()
                // All other paths require a valid JWT
                .anyExchange().authenticated()
            )
            // Validate all inbound Bearer tokens using our symmetric HS256 key
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtDecoder(reactiveJwtDecoder()))
            );

        return http.build();
    }

    /**
     * Creates a symmetric HS256 ReactiveJwtDecoder using the shared JWT_SECRET.
     * This must match the key used in AuthController when signing tokens.
     */
    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        SecretKey secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(secretKey).build();
    }
}
