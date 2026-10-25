# Unit Testing

Unit tests use JUnit 5 + Mockito. They run fast (no Docker, no database, no Kafka) because all dependencies are mocked.

## Run All Unit Tests

```bash
cd PostTradeClearingPlatform
mvn test
```

Expected output:

```
[INFO] Results:
[INFO] Tests run: N, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Integration tests (`*IntegrationTest.java`, `*RoutingTest.java`) are **excluded** from `mvn test` by the Surefire plugin configuration.

---

## Run Tests for a Single Service

```bash
# Only trade-service tests
mvn test -pl trade-service -am

# Only clearing-service tests
mvn test -pl clearing-service -am
```

---

## Run a Single Test Class

```bash
mvn test -pl trade-service -am \
  -Dtest=TradeProcessingServiceTest
```

## Run a Single Test Method

```bash
mvn test -pl position-service -am \
  -Dtest=PositionValidationServiceTest#shouldValidateAndProcessWhenSellerHasSufficientShares
```

---

## Unit Test Inventory

### trade-service

| File | What It Tests |
|------|--------------|
| `TradeControllerTest.java` | REST endpoint: POST /api/v1/trades — validates request, checks 201/400 responses |
| `TradeProcessingServiceTest.java` | Trade submission logic, Kafka event publishing |

### position-service

| File | What It Tests |
|------|--------------|
| `PositionValidationServiceTest.java` | Position validation: sufficient balance, rejection on insufficient qty, deterministic lock ordering |
| `TradeSubmittedConsumerTest.java` | Kafka consumer: correct delegation to service, ACK on success, no ACK on failure |

### clearing-service

| File | What It Tests |
|------|--------------|
| `ClearingServiceTest.java` | Settlement lifecycle: PENDING on VALIDATED, FAILED on REJECTED, SETTLED when both sides confirmed |
| `ClearingControllerTest.java` | REST endpoints: GET /api/v1/clearing/settlements/{tradeId} |

### notification-service

| File | What It Tests |
|------|--------------|
| `NotificationServiceTest.java` | Notification creation on settlement-completed event |
| `NotificationControllerTest.java` | REST endpoint: GET /api/v1/notifications/trades/{tradeId} |

### reference-data-service

| File | What It Tests |
|------|--------------|
| `InstrumentServiceTest.java` | Instrument lookup, creation, validation |
| `ParticipantServiceTest.java` | Participant lookup, creation, validation |

### corporate-action-service

| File | What It Tests |
|------|--------------|
| `CorporateActionServiceTest.java` | Corporate action processing and event publishing |

### market-integration-gateway

| File | What It Tests |
|------|--------------|
| `MarketTradeControllerTest.java` | REST endpoint for market order ingestion |
| `TradeTranslatorTest.java` | Translation from FIX/external format to internal TradeSubmittedEvent |

---

## Test Patterns Used

### Standard Unit Test (Mockito)

```java
@ExtendWith(MockitoExtension.class)
class ClearingServiceTest {

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementEventPublisher eventPublisher;

    @InjectMocks
    private ClearingService clearingService;

    @Test
    void shouldCreatePendingSettlementOnValidatedTrade() {
        // Arrange
        TradeValidatedEvent event = new TradeValidatedEvent(
            "trade-123", TradeValidationStatus.VALIDATED, null, Instant.now()
        );
        when(settlementRepository.findByTradeId("trade-123"))
            .thenReturn(Optional.empty());

        // Act
        clearingService.handleTradeValidated(event);

        // Assert
        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository).save(captor.capture());
        assertEquals(SettlementStatus.PENDING, captor.getValue().getStatus());
    }
}
```

### Controller Slice Test (MockMvc)

```java
@WebMvcTest(TradeController.class)
@Import(SecurityConfig.class)
class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradeProcessingService tradeService;

    @Test
    void submitTrade_validRequest_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"instrumentId":"AAPL","quantity":100,"price":150.00,
                     "buyerAccountId":"ACC001","sellerAccountId":"ACC002"}
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }
}
```

---

## Troubleshooting Unit Tests

### `UnsatisfiedDependencyException` with `@InjectMocks`

Mockito's `@InjectMocks` uses constructor injection. If you add a new dependency to a class, add a corresponding `@Mock` field in the test.

Example — after adding `PositionUpdatedPublisher` to `PositionValidationService`:

```java
@Mock
private PositionUpdatedPublisher positionUpdatedPublisher;  // must be present
```

### `WantedButNotInvoked` assertion failure

The method was called on the real object, not the mock, or with different arguments. Use `verify(mock, times(0))` to confirm it wasn't called, or check `ArgumentCaptor` to see what arguments were actually passed.

### Tests pass locally but fail in CI

Usually a test that relies on ordering of `HashMap` entries or `Instant.now()` being consistent. Use fixed test data and `Mockito.mockStatic(Instant.class)` where needed.
