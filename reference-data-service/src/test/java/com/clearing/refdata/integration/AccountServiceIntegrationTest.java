package com.clearing.refdata.integration;

import com.clearing.refdata.model.Account;
import com.clearing.refdata.model.AccountStatus;
import com.clearing.refdata.model.Participant;
import com.clearing.refdata.model.ParticipantStatus;
import com.clearing.refdata.repository.AccountRepository;
import com.clearing.refdata.repository.ParticipantRepository;
import com.clearing.refdata.service.AccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for AccountService (and AccountRepository) against a real PostgreSQL database.
 *
 * <p><strong>Test Pyramid layer: Integration</strong>
 * <ul>
 *   <li>Uses a real PostgreSQL Testcontainer with Flyway V1+V2 migrations applied.</li>
 *   <li>Verifies schema correctness (FK constraint to participants), index-backed queries,
 *       and balance arithmetic as persisted to the database.</li>
 *   <li>No Spring MVC, no Kafka, no Redis, no Security — this is a JPA slice test.</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AccountServiceIntegrationTest {

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
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("spring.flyway.validate-on-migrate", () -> "true");
        registry.add("spring.flyway.table", () -> "refdata_flyway_history");
        // Redis is not loaded in JPA slice — Spring caching is not active here
        registry.add("spring.cache.type", () -> "none");
    }

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    // Service wired directly — avoids Spring full-context overhead
    private AccountService accountService;

    private static final String PARTICIPANT_ID = "PART-TEST-001";

    @BeforeEach
    void setup() {
        accountService = new AccountService(accountRepository);

        // FK constraint requires participant to exist first
        if (participantRepository.findByParticipantId(PARTICIPANT_ID).isEmpty()) {
            participantRepository.save(new Participant(
                    UUID.randomUUID(), PARTICIPANT_ID, "Test Participant", ParticipantStatus.ACTIVE));
        }
    }

    @AfterEach
    void cleanDatabase() {
        accountRepository.deleteAll();
    }

    // ── Schema validation ────────────────────────────────────────────────────

    @Test
    @DisplayName("Flyway V2 migration creates accounts table and it is queryable")
    void flywayMigration_createsAccountsTable() {
        assertThat(accountRepository.count()).isZero();
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create — persists account to DB with ACTIVE status")
    void create_persistsAccountToDatabase() {
        Account savedAccount = accountService.create(
                "ACC-001", PARTICIPANT_ID, new BigDecimal("5000.00"), "USD");

        Optional<Account> reloaded = accountRepository.findByAccountId("ACC-001");

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getBalance()).isEqualByComparingTo("5000.00");
        assertThat(reloaded.get().getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(reloaded.get().getCurrency()).isEqualTo("USD");
        assertThat(reloaded.get().getId()).isNotNull();
    }

    @Test
    @DisplayName("create — FK constraint prevents account without existing participant")
    void create_failsWhenParticipantDoesNotExist() {
        // Spring data will propagate the DB constraint violation at flush time
        assertThatThrownBy(() -> {
            accountService.create("ACC-X", "NO-SUCH-PARTICIPANT", BigDecimal.ZERO, "USD");
            accountRepository.flush(); // force constraint evaluation
        }).isInstanceOf(Exception.class); // DataIntegrityViolationException or similar
    }

    // ── Credit ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("credit — balance is correctly updated in database after credit")
    void credit_updatesBalanceInDatabase() {
        accountService.create("ACC-002", PARTICIPANT_ID, new BigDecimal("1000.00"), "USD");

        accountService.credit("ACC-002", new BigDecimal("500.00"));

        Account reloaded = accountRepository.findByAccountId("ACC-002").orElseThrow();
        assertThat(reloaded.getBalance()).isEqualByComparingTo("1500.00");
    }

    // ── Debit ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("debit — balance is correctly updated in database after debit")
    void debit_updatesBalanceInDatabase() {
        accountService.create("ACC-003", PARTICIPANT_ID, new BigDecimal("2000.00"), "USD");

        accountService.debit("ACC-003", new BigDecimal("800.00"));

        Account reloaded = accountRepository.findByAccountId("ACC-003").orElseThrow();
        assertThat(reloaded.getBalance()).isEqualByComparingTo("1200.00");
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByParticipantId — returns only accounts for the given participant")
    void findByParticipantId_returnsMatchingAccounts() {
        accountService.create("ACC-A1", PARTICIPANT_ID, BigDecimal.ZERO, "USD");
        accountService.create("ACC-A2", PARTICIPANT_ID, BigDecimal.ZERO, "USD");

        List<Account> result = accountRepository.findByParticipantId(PARTICIPANT_ID);

        assertThat(result).hasSize(2)
                .extracting(Account::getAccountId)
                .containsExactlyInAnyOrder("ACC-A1", "ACC-A2");
    }

    @Test
    @DisplayName("findByStatus — returns only accounts with matching status")
    void findByStatus_returnsMatchingAccounts() {
        accountService.create("ACC-S1", PARTICIPANT_ID, BigDecimal.ZERO, "USD");
        accountService.create("ACC-S2", PARTICIPANT_ID, BigDecimal.ZERO, "USD");
        accountService.suspend("ACC-S1");

        List<Account> activeAccounts = accountRepository.findByStatus(AccountStatus.ACTIVE);
        List<Account> suspendedAccounts = accountRepository.findByStatus(AccountStatus.SUSPENDED);

        assertThat(activeAccounts).hasSize(1)
                .extracting(Account::getAccountId).containsExactly("ACC-S2");
        assertThat(suspendedAccounts).hasSize(1)
                .extracting(Account::getAccountId).containsExactly("ACC-S1");
    }

    // ── Status lifecycle ─────────────────────────────────────────────────────

    @Test
    @DisplayName("suspend then close — status transitions persist correctly")
    void statusLifecycle_persistsCorrectly() {
        accountService.create("ACC-LC", PARTICIPANT_ID, BigDecimal.ZERO, "USD");

        accountService.suspend("ACC-LC");
        assertThat(accountRepository.findByAccountId("ACC-LC").orElseThrow().getStatus())
                .isEqualTo(AccountStatus.SUSPENDED);

        accountService.close("ACC-LC");
        assertThat(accountRepository.findByAccountId("ACC-LC").orElseThrow().getStatus())
                .isEqualTo(AccountStatus.CLOSED);
    }
}
