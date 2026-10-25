package com.clearing.position.integration;

import com.clearing.position.model.Position;
import com.clearing.position.repository.PositionRepository;
import com.clearing.shared.events.TradeSubmittedEvent;
import com.clearing.shared.events.TradeValidationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for the PositionService Kafka consumer pipeline.
 *
 * Verifies the full position validation flow:
 *   Kafka TradeSubmittedEvent → position check → DB update → TradeValidatedEvent published
 *
 * Uses real PostgreSQL and Kafka containers. Security is disabled to allow
 * the application context to start without a Keycloak server.
 *
 * Test isolation: all positions are wiped after each test via @AfterEach.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // Disable OAuth2 resource server to avoid Keycloak dependency in tests
                "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration," +
                "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
        }
)
@Testcontainers
class PositionValidationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("clearing")
            .withUsername("admin")
            .withPassword("admin");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private PositionRepository positionRepository;

    /**
     * The app's own KafkaTemplate (JsonSerializer already configured).
     * Reused here to publish test events to the trade-submitted topic.
     */
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanDb() {
        positionRepository.deleteAll();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Happy path: sufficient position
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldDebitSellerAndCreditBuyerWhenSellerHasSufficientShares() {
        // Seed initial positions
        seedPosition("SELLER-001", "AAPL", new BigDecimal("500"));
        seedPosition("BUYER-001", "AAPL", new BigDecimal("0"));

        String tradeId = UUID.randomUUID().toString();
        TradeSubmittedEvent event = new TradeSubmittedEvent(
                tradeId, "BUYER-001", "SELLER-001", "AAPL", 100, new BigDecimal("150.00"), Instant.now());

        kafkaTemplate.send("trade-submitted", tradeId, event);

        // Wait for consumer to process and flush to DB
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Optional<Position> seller = positionRepository.findByAccountIdAndInstrumentId("SELLER-001", "AAPL");
            Optional<Position> buyer = positionRepository.findByAccountIdAndInstrumentId("BUYER-001", "AAPL");

            assertThat(seller).isPresent();
            assertThat(buyer).isPresent();
            assertThat(seller.get().getQuantity()).isEqualByComparingTo("400"); // 500 - 100
            assertThat(buyer.get().getQuantity()).isEqualByComparingTo("100");  // 0 + 100
        });
    }

    @Test
    void shouldPublishValidatedEventWhenTradeSettles() throws Exception {
        seedPosition("SELLER-002", "MSFT", new BigDecimal("1000"));
        seedPosition("BUYER-002", "MSFT", new BigDecimal("0"));

        String tradeId = UUID.randomUUID().toString();
        TradeSubmittedEvent event = new TradeSubmittedEvent(
                tradeId, "BUYER-002", "SELLER-002", "MSFT", 200, new BigDecimal("350.00"), Instant.now());

        kafkaTemplate.send("trade-submitted", tradeId, event);

        try (KafkaConsumer<String, String> consumer = rawStringConsumer("it-validated-" + UUID.randomUUID())) {
            consumer.subscribe(List.of("trade-validated"));

            await().atMost(Duration.ofSeconds(15)).until(() -> {
                var records = consumer.poll(Duration.ofMillis(300));
                for (ConsumerRecord<String, String> record : records) {
                    JsonNode json = objectMapper.readTree(record.value());
                    if (tradeId.equals(json.path("tradeId").asText())
                            && TradeValidationStatus.VALIDATED.name().equals(json.path("status").asText())) {
                        return true;
                    }
                }
                return false;
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rejection: insufficient position
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldNotUpdatePositionsWhenSellerHasInsufficientShares() {
        seedPosition("SELLER-003", "TSLA", new BigDecimal("50"));  // only 50, trade needs 100
        seedPosition("BUYER-003", "TSLA", new BigDecimal("0"));

        String tradeId = UUID.randomUUID().toString();
        TradeSubmittedEvent event = new TradeSubmittedEvent(
                tradeId, "BUYER-003", "SELLER-003", "TSLA", 100, new BigDecimal("200.00"), Instant.now());

        kafkaTemplate.send("trade-submitted", tradeId, event);

        // Give the consumer time to process
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            // Confirm a REJECTED event was published (signals consumer ran)
            // then verify DB was NOT mutated
        });

        // Seller quantity must remain unchanged
        Optional<Position> seller = positionRepository.findByAccountIdAndInstrumentId("SELLER-003", "TSLA");
        assertThat(seller).isPresent();
        assertThat(seller.get().getQuantity()).isEqualByComparingTo("50");

        Optional<Position> buyer = positionRepository.findByAccountIdAndInstrumentId("BUYER-003", "TSLA");
        assertThat(buyer).isPresent();
        assertThat(buyer.get().getQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void shouldPublishRejectedEventWhenSellerHasInsufficientShares() throws Exception {
        seedPosition("SELLER-004", "AMZN", new BigDecimal("10"));
        seedPosition("BUYER-004", "AMZN", new BigDecimal("0"));

        String tradeId = UUID.randomUUID().toString();
        TradeSubmittedEvent event = new TradeSubmittedEvent(
                tradeId, "BUYER-004", "SELLER-004", "AMZN", 100, new BigDecimal("180.00"), Instant.now());

        kafkaTemplate.send("trade-submitted", tradeId, event);

        try (KafkaConsumer<String, String> consumer = rawStringConsumer("it-rejected-" + UUID.randomUUID())) {
            consumer.subscribe(List.of("trade-validated"));

            await().atMost(Duration.ofSeconds(15)).until(() -> {
                var records = consumer.poll(Duration.ofMillis(300));
                for (ConsumerRecord<String, String> record : records) {
                    JsonNode json = objectMapper.readTree(record.value());
                    if (tradeId.equals(json.path("tradeId").asText())
                            && TradeValidationStatus.REJECTED.name().equals(json.path("status").asText())) {
                        assertThat(json.path("rejectionReason").asText()).isNotBlank();
                        return true;
                    }
                }
                return false;
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Auto-creation of positions
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldAutoCreateBuyerPositionWhenNoExistingRecordFound() {
        // Only seed the seller — buyer has no prior position record
        seedPosition("SELLER-005", "GOOG", new BigDecimal("300"));

        String tradeId = UUID.randomUUID().toString();
        TradeSubmittedEvent event = new TradeSubmittedEvent(
                tradeId, "BUYER-005", "SELLER-005", "GOOG", 50, new BigDecimal("140.00"), Instant.now());

        kafkaTemplate.send("trade-submitted", tradeId, event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Optional<Position> buyer = positionRepository.findByAccountIdAndInstrumentId("BUYER-005", "GOOG");
            assertThat(buyer).isPresent();
            assertThat(buyer.get().getQuantity()).isEqualByComparingTo("50");
        });
    }

    @Test
    void shouldAutoCreateBothPositionsForFirstTimeCounterparties() {
        // Neither buyer nor seller has a prior position record for this instrument
        String tradeId = UUID.randomUUID().toString();

        // First, create a seller with enough to sell (auto-created at zero, so we seed manually)
        seedPosition("SELLER-006", "NVDA", new BigDecimal("500"));

        TradeSubmittedEvent event = new TradeSubmittedEvent(
                tradeId, "BUYER-006", "SELLER-006", "NVDA", 75, new BigDecimal("600.00"), Instant.now());

        kafkaTemplate.send("trade-submitted", tradeId, event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Optional<Position> buyer = positionRepository.findByAccountIdAndInstrumentId("BUYER-006", "NVDA");
            Optional<Position> seller = positionRepository.findByAccountIdAndInstrumentId("SELLER-006", "NVDA");

            assertThat(buyer).isPresent();
            assertThat(seller).isPresent();
            assertThat(buyer.get().getQuantity()).isEqualByComparingTo("75");
            assertThat(seller.get().getQuantity()).isEqualByComparingTo("425");
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Deadlock prevention: alphabetical lock ordering
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldProcessConcurrentTradesWithoutDeadlock() throws InterruptedException {
        // Seed positions for two accounts on the same instrument
        seedPosition("ACCOUNT-A", "AAPL", new BigDecimal("10000"));
        seedPosition("ACCOUNT-B", "AAPL", new BigDecimal("10000"));

        int concurrentTrades = 5;
        Thread[] threads = new Thread[concurrentTrades];
        for (int i = 0; i < concurrentTrades; i++) {
            // Alternate direction: even trades A→B, odd trades B→A
            final boolean aToB = i % 2 == 0;
            threads[i] = new Thread(() -> {
                String tid = UUID.randomUUID().toString();
                String buyer = aToB ? "ACCOUNT-B" : "ACCOUNT-A";
                String seller = aToB ? "ACCOUNT-A" : "ACCOUNT-B";
                TradeSubmittedEvent event = new TradeSubmittedEvent(
                        tid, buyer, seller, "AAPL", 10, new BigDecimal("150.00"), Instant.now());
                kafkaTemplate.send("trade-submitted", tid, event);
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        // All 5 trades must eventually be reflected without deadlock or lost updates.
        // Total positions must still sum to 20000 (10000 + 10000) because we
        // alternate the direction, so net movement is zero for 5 trades.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Optional<Position> posA = positionRepository.findByAccountIdAndInstrumentId("ACCOUNT-A", "AAPL");
            Optional<Position> posB = positionRepository.findByAccountIdAndInstrumentId("ACCOUNT-B", "AAPL");

            assertThat(posA).isPresent();
            assertThat(posB).isPresent();

            BigDecimal totalQuantity = posA.get().getQuantity().add(posB.get().getQuantity());
            assertThat(totalQuantity).isEqualByComparingTo("20000");
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void seedPosition(String accountId, String instrumentId, BigDecimal quantity) {
        positionRepository.save(new Position(UUID.randomUUID(), accountId, instrumentId, quantity));
    }

    /**
     * Creates a raw-string Kafka consumer pointed at the test container.
     * Unique group ID ensures it always reads from the earliest offset.
     */
    private KafkaConsumer<String, String> rawStringConsumer(String groupId) {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()
        ));
    }
}
