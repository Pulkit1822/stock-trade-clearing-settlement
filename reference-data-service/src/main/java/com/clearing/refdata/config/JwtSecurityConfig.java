package com.clearing.refdata.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Configures Spring Security to validate inbound requests using the shared HS256 JWT secret.
 *
 * <p>Spring Security's auto-configuration only supports asymmetric (RSA/EC) keys via
 * {@code issuer-uri} or {@code public-key-location}. Symmetric HS256 requires a
 * programmatic {@link JwtDecoder} bean — this class provides it.
 *
 * <p>The same secret must be set in the API Gateway ({@code JWT_SECRET} env var)
 * so tokens issued there can be verified here without any network call.
 */
import org.springframework.context.annotation.Profile;

@Configuration
@EnableWebSecurity
@Profile("!test")
public class JwtSecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Kubernetes liveness/readiness probes and Prometheus scraping must be open
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt -> jwt.decoder(jwtDecoder()))
            );

        return http.build();
    }

    /**
     * Builds a {@link NimbusJwtDecoder} from the shared HMAC-SHA256 secret.
     * Must match the key used in the API Gateway's {@code AuthController}.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        SecretKey secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }
}
