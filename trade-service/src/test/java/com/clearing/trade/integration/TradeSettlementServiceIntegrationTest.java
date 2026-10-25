package com.clearing.trade.integration;

import com.clearing.shared.events.SettlementCompletedEvent;
import com.clearing.trade.model.Trade;
import com.clearing.trade.model.TradeStatus;
import com.clearing.trade.repository.TradeRepository;
import com.clearing.trade.service.TradeSettlementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TradeSettlementService against a real PostgreSQL database.
 *
 * <p><strong>Test Pyramid layer: Integration</strong>
 * <ul>
 *   <li>Uses a real PostgreSQL Testcontainer with Flyway migrations applied.</li>
 *   <li>Tests that {@link TradeSettlementService#markTradeAsSettled} actually persists
 *       the SETTLED status to the DB via the JPA repository.</li>
 *   <li>No Spring MVC stack, no Kafka, no Security — only the JPA slice + service wired manually.</li>
 * </ul>
 *
 * <p>Kafka consumer wiring is handled separately by the E2E test
 * ({@link TradeServiceIntegrationTest}), which spins up the full application context.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class TradeSettlementServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("clearing")
            .withUsername("admin")
            .withPassword("admin");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Tell Flyway to create the schema so JPA can validate it
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("spring.flyway.validate-on-migrate", () -> "true");
    }

    @Autowired
    private TradeRepository tradeRepository;

    // Wire service directly — no Spring context overhead for Kafka, Security, etc.
    private TradeSettlementService tradeSettlementService() {
        return new TradeSettlementService(tradeRepository);
    }

    @AfterEach
    void cleanDatabase() {
        tradeRepository.deleteAll();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Happy path: DB record is updated to SETTLED
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldPersistSettledStatusWhenTradeExistsAndIsSubmitted() {
        Trade savedTrade = tradeRepository.save(buildTrade(TradeStatus.SUBMITTED));
        SettlementCompletedEvent event = buildEvent(savedTrade.getId().toString());

        tradeSettlementService().markTradeAsSettled(event);

        Trade updatedTrade = tradeRepository.findById(savedTrade.getId()).orElseThrow();
        assertThat(updatedTrade.getStatus())
                .as("Trade status must be SETTLED after processing SettlementCompletedEvent")
                .isEqualTo(TradeStatus.SETTLED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Idempotency: second call on an already-SETTLED trade does not double-write
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldNotModifyTradeWhenAlreadySettled() {
        Trade savedTrade = tradeRepository.save(buildTrade(TradeStatus.SETTLED));
        Instant updatedAtBefore = savedTrade.getUpdatedAt();
        SettlementCompletedEvent event = buildEvent(savedTrade.getId().toString());

        // Second settlement event for the same trade — must be a no-op
        tradeSettlementService().markTradeAsSettled(event);

        Trade reloaded = tradeRepository.findById(savedTrade.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TradeStatus.SETTLED);
        // updatedAt should not have changed on an idempotent skip
        assertThat(reloaded.getUpdatedAt()).isEqualTo(updatedAtBefore);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Unknown tradeId: service is tolerant — no exception, no DB writes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldNotThrowWhenTradeIdDoesNotExistInDatabase() {
        String unknownTradeId = UUID.randomUUID().toString();
        SettlementCompletedEvent event = buildEvent(unknownTradeId);

        // Must not throw — consumer relies on this for graceful handling
        tradeSettlementService().markTradeAsSettled(event);

        assertThat(tradeRepository.count()).isZero();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Flyway schema validation: ensure DB has 'trades' table with all columns
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldHaveTradesTableCreatedByFlywayMigration() {
        // If Flyway migration is missing or schema is wrong, @DataJpaTest startup fails.
        // This test confirms the table is queryable and count() works on a fresh DB.
        assertThat(tradeRepository.count()).isZero();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findByStatus query method
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldFindTradesByStatus() {
        tradeRepository.save(buildTrade(TradeStatus.SUBMITTED));
        tradeRepository.save(buildTrade(TradeStatus.SETTLED));

        assertThat(tradeRepository.findByStatus(TradeStatus.SUBMITTED)).hasSize(1);
        assertThat(tradeRepository.findByStatus(TradeStatus.SETTLED)).hasSize(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Trade buildTrade(TradeStatus status) {
        return new Trade(
                UUID.randomUUID(),
                "BUYER-001",
                "SELLER-001",
                "AAPL",
                new BigDecimal("100"),
                new BigDecimal("150.00"),
                Instant.now(),
                status
        );
    }

    private SettlementCompletedEvent buildEvent(String tradeId) {
        return new SettlementCompletedEvent(tradeId, "BUYER-001", "SELLER-001", Instant.now());
    }
}
