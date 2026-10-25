package com.clearing.trade.integration;

import com.clearing.shared.dto.TradeRequest;
import com.clearing.trade.model.TradeStatus;
import com.clearing.trade.repository.TradeRepository;
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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for TradeService using real PostgreSQL and Kafka containers.
 *
 * Verifies the full trade submission flow:
 *   POST /api/v1/trades → DB persist → TradeSubmittedEvent on Kafka
 *
 * Security is disabled to focus testing on business logic.
 * Each test gets a clean DB state via @AfterEach.
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
class TradeServiceIntegrationTest {

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
    private TestRestTemplate restTemplate;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanDb() {
        tradeRepository.deleteAll();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldReturn201AndPersistTradeWhenRequestIsValid() {
        TradeRequest request = new TradeRequest("AAPL", "BUYER-001", "SELLER-001", 100, new BigDecimal("150.00"));

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/trades", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // DB assertion — Flyway migrations ran against the real container
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(tradeRepository.count()).isEqualTo(1)
        );

        var saved = tradeRepository.findAll().get(0);
        assertThat(saved.getInstrumentId()).isEqualTo("AAPL");
        assertThat(saved.getBuyerAccountId()).isEqualTo("BUYER-001");
        assertThat(saved.getSellerAccountId()).isEqualTo("SELLER-001");
        assertThat(saved.getQuantity()).isEqualByComparingTo("100");
        assertThat(saved.getPrice()).isEqualByComparingTo("150.00");
        assertThat(saved.getStatus()).isEqualTo(TradeStatus.SUBMITTED);
    }

    @Test
    void shouldPublishTradeSubmittedEventToKafkaOnSuccessfulTrade() throws Exception {
        TradeRequest request = new TradeRequest("MSFT", "BUYER-002", "SELLER-002", 50, new BigDecimal("350.10"));

        restTemplate.postForEntity("/api/v1/trades", request, String.class);

        // Consume from the real Kafka container and verify event payload
        try (KafkaConsumer<String, String> consumer = rawStringConsumer("it-verify-" + UUID.randomUUID())) {
            consumer.subscribe(List.of("trade-submitted"));

            await().atMost(Duration.ofSeconds(15)).until(() -> {
                var records = consumer.poll(Duration.ofMillis(300));
                for (ConsumerRecord<String, String> record : records) {
                    JsonNode json = objectMapper.readTree(record.value());
                    if ("MSFT".equals(json.path("instrumentId").asText())
                            && "BUYER-002".equals(json.path("buyerAccountId").asText())
                            && "SELLER-002".equals(json.path("sellerAccountId").asText())) {
                        return true;
                    }
                }
                return false;
            });
        }
    }

    @Test
    void shouldIncludeTradeIdInKafkaEventMatchingDbRecord() throws Exception {
        TradeRequest request = new TradeRequest("TSLA", "BUYER-003", "SELLER-003", 10, new BigDecimal("200.00"));

        restTemplate.postForEntity("/api/v1/trades", request, String.class);

        // Wait for DB record
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(tradeRepository.count()).isEqualTo(1));

        String persistedTradeId = tradeRepository.findAll().get(0).getId().toString();

        // Confirm Kafka event carries the same tradeId
        try (KafkaConsumer<String, String> consumer = rawStringConsumer("it-id-verify-" + UUID.randomUUID())) {
            consumer.subscribe(List.of("trade-submitted"));

            await().atMost(Duration.ofSeconds(15)).until(() -> {
                var records = consumer.poll(Duration.ofMillis(300));
                for (ConsumerRecord<String, String> record : records) {
                    JsonNode json = objectMapper.readTree(record.value());
                    if (persistedTradeId.equals(json.path("tradeId").asText())) {
                        return true;
                    }
                }
                return false;
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Validation failures
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldReturn400WhenQuantityIsZero() {
        TradeRequest request = new TradeRequest("AAPL", "BUYER-001", "SELLER-001", 0, new BigDecimal("150.00"));

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/trades", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tradeRepository.count()).isZero();
    }

    @Test
    void shouldReturn400WhenQuantityIsNegative() {
        TradeRequest request = new TradeRequest("AAPL", "BUYER-001", "SELLER-001", -5, new BigDecimal("150.00"));

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/trades", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tradeRepository.count()).isZero();
    }

    @Test
    void shouldReturn400WhenInstrumentIdIsBlank() {
        TradeRequest request = new TradeRequest("", "BUYER-001", "SELLER-001", 10, new BigDecimal("100.00"));

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/trades", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tradeRepository.count()).isZero();
    }

    @Test
    void shouldReturn400WhenBuyerAccountIdIsBlank() {
        TradeRequest request = new TradeRequest("AAPL", "", "SELLER-001", 10, new BigDecimal("100.00"));

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/trades", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tradeRepository.count()).isZero();
    }

    @Test
    void shouldReturn400WhenPriceIsBelowMinimum() {
        TradeRequest request = new TradeRequest("AAPL", "BUYER-001", "SELLER-001", 10, new BigDecimal("0.00"));

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/trades", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tradeRepository.count()).isZero();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a raw string Kafka consumer pointed at the test Kafka container.
     * Uses a unique group ID per call so each consumer always reads from offset 0 (earliest).
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
