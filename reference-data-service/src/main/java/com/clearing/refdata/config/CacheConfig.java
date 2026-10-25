package com.clearing.refdata.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Spring Cache with Redis as the backing store.
 * TTL is configured in application.yml (spring.cache.redis.time-to-live = 600000ms = 10 min).
 *
 * Cache names used:
 * - "instruments"  → single instrument lookups by instrumentId
 * - "participants" → single participant lookups by participantId
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
