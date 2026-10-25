package com.clearing.gateway.routing;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Integration tests for API Gateway routing and security.
 *
 * Uses:
 *   - Testcontainers Redis    → real rate-limiter backend
 *   - WireMock                → stubs downstream services (trade-service, position-service, etc.)
 *   - WebTestClient           → reactive HTTP client for asserting responses
 *
 * JWT security is disabled so tests focus entirely on routing behaviour.
 * Each test group verifies a separate concern:
 *   1. Route forwarding     — correct path mapped to correct downstream
 *   2. 404 on unknown paths — gateway rejects unknown routes
 *   3. Actuator endpoints   — health endpoint accessible without auth
 *   4. Correlation ID       — gateway injects X-Correlation-ID on all forwarded requests
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ApiGatewayRoutingTest {

    // Satisfies SecurityConfig's JWT dependency without a real Keycloak
    @MockBean
    ReactiveJwtDecoder jwtDecoder;

    // ─────────────────────────────────────────────────────────────────────────
    // Infrastructure containers
    // ─────────────────────────────────────────────────────────────────────────

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    /**
     * A single WireMock server handles all downstream stubs.
     * Started in a static initializer so its port is available when
     * @DynamicPropertySource runs during Spring context initialization —
     * which happens before @BeforeAll lifecycle methods.
     */
    static final WireMockServer wireMock;

    static {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // Redis
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // Point all route URIs to the WireMock server
        String wireMockBaseUrl = "http://localhost:" + wireMock.port();
        registry.add("TRADE_SERVICE_URI", () -> wireMockBaseUrl);
        registry.add("POSITION_SERVICE_URI", () -> wireMockBaseUrl);
        registry.add("CLEARING_SERVICE_URI", () -> wireMockBaseUrl);
        registry.add("REFERENCE_DATA_SERVICE_URI", () -> wireMockBaseUrl);
        registry.add("CORPORATE_ACTION_SERVICE_URI", () -> wireMockBaseUrl);
        registry.add("NOTIFICATION_SERVICE_URI", () -> wireMockBaseUrl);
        registry.add("MARKET_GATEWAY_URI", () -> wireMockBaseUrl);
    }

    @LocalServerPort
    int port;

    @Autowired
    WebTestClient webTestClient;

    // ─────────────────────────────────────────────────────────────────────────
    // Route forwarding
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldForwardTradeRequestToTradeService() {
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/trades"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"abc123\",\"status\":\"SUBMITTED\"}")));

        webTestClient.post().uri("/api/v1/trades")
                .header("Content-Type", "application/json")
                .bodyValue("{\"instrumentId\":\"AAPL\",\"buyerAccountId\":\"B1\",\"sellerAccountId\":\"S1\",\"quantity\":10,\"price\":\"150.00\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUBMITTED");
    }

    @Test
    void shouldForwardPositionRequestToPositionService() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/positions/ACCOUNT-001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"instrumentId\":\"AAPL\",\"quantity\":\"100\"}]")));

        webTestClient.get().uri("/api/v1/positions/ACCOUNT-001")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].instrumentId").isEqualTo("AAPL");
    }

    @Test
    void shouldForwardClearingRequestToClearingService() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/clearing/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ACTIVE\"}")));

        webTestClient.get().uri("/api/v1/clearing/status")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldForwardReferenceDataInstrumentsRequest() {
        wireMock.stubFor(get(urlPathEqualTo("/instruments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        webTestClient.get().uri("/instruments")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldForwardCorporateActionRequest() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/corporate-actions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        webTestClient.get().uri("/api/v1/corporate-actions")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldForwardNotificationRequest() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/notifications"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        webTestClient.get().uri("/api/v1/notifications")
                .exchange()
                .expectStatus().isOk();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Unknown routes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldReturn404ForUnknownPath() {
        webTestClient.get().uri("/api/v1/unknown-service/resource")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldReturn404ForRootPath() {
        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Actuator (must be accessible without auth per SecurityConfig)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldExposeHealthEndpointWithoutAuthentication() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Correlation ID injection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldInjectCorrelationIdHeaderOnForwardedTradeRequests() {
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/trades"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"xyz\"}")));

        webTestClient.post().uri("/api/v1/trades")
                .header("Content-Type", "application/json")
                .bodyValue("{\"instrumentId\":\"TSLA\",\"buyerAccountId\":\"B2\",\"sellerAccountId\":\"S2\",\"quantity\":5,\"price\":\"200.00\"}")
                .exchange()
                .expectStatus().isCreated();

        // Verify that the gateway injected X-Correlation-ID before forwarding
        wireMock.verify(postRequestedFor(urlPathEqualTo("/api/v1/trades"))
                .withHeader("X-Correlation-ID", matching(".+")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Downstream error passthrough
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldPassThroughDownstream503ToClient() {
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/trades"))
                .willReturn(aResponse().withStatus(503)));

        webTestClient.post().uri("/api/v1/trades")
                .header("Content-Type", "application/json")
                .bodyValue("{\"instrumentId\":\"MSFT\",\"buyerAccountId\":\"B3\",\"sellerAccountId\":\"S3\",\"quantity\":1,\"price\":\"1.00\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldPassThroughDownstream400ToClient() {
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/trades"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"quantity must be at least 1\"}")));

        webTestClient.post().uri("/api/v1/trades")
                .header("Content-Type", "application/json")
                .bodyValue("{\"instrumentId\":\"MSFT\",\"buyerAccountId\":\"B3\",\"sellerAccountId\":\"S3\",\"quantity\":0,\"price\":\"1.00\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
