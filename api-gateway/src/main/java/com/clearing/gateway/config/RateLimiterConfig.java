package com.clearing.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Configuration
public class RateLimiterConfig {

    /**
     * Resolves the key used by Redis to rate limit traffic per user.
     * Extracts the Subject (user ID) from the active JWT token.
     * Defaults to "anonymous" if no token is found.
     */
    @Bean
    public KeyResolver principalNameKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(auth -> {
                    if (auth instanceof JwtAuthenticationToken jwt) {
                        return jwt.getToken().getSubject(); // Extracted from Keycloak Token
                    }
                    return auth.getName();
                })
                .defaultIfEmpty(
                    Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                        .map(addr -> addr.getAddress().getHostAddress())
                        .orElse("anonymous"));
    }
}
