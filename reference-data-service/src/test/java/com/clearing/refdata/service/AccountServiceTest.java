package com.clearing.refdata.service;

import com.clearing.refdata.model.Account;
import com.clearing.refdata.model.AccountStatus;
import com.clearing.refdata.repository.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AccountService}.
 *
 * <p><strong>Test Pyramid layer: Unit</strong>
 * <ul>
 *   <li>Uses Mockito to stub {@link AccountRepository} — no DB, no Spring context.</li>
 *   <li>Tests business logic in complete isolation: guard clauses, state transitions,
 *       balance arithmetic.</li>
 *   <li>Runs in milliseconds.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create — persists ACTIVE account with the given initial balance")
    void create_persistsActiveAccountWithInitialBalance() {
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Account result = accountService.create("ACC-001", "PART-001",
                new BigDecimal("10000.00"), "USD");

        assertThat(result.getAccountId()).isEqualTo("ACC-001");
        assertThat(result.getParticipantId()).isEqualTo("PART-001");
        assertThat(result.getBalance()).isEqualByComparingTo("10000.00");
        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    @DisplayName("create — rejects negative initial balance before touching DB")
    void create_rejectsNegativeInitialBalance() {
        assertThatThrownBy(() ->
                accountService.create("ACC-002", "PART-001", new BigDecimal("-1.00"), "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Initial balance cannot be negative");

        verify(accountRepository, never()).save(any());
    }

    // ── credit ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("credit — increases balance by the credit amount")
    void credit_increasesBalance() {
        Account existingAccount = buildAccount("ACC-001", new BigDecimal("1000.00"));
        when(accountRepository.findByAccountId("ACC-001")).thenReturn(Optional.of(existingAccount));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Account> result = accountService.credit("ACC-001", new BigDecimal("500.00"));

        assertThat(result).isPresent();
        assertThat(result.get().getBalance()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("credit — rejects zero or negative amount")
    void credit_rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> accountService.credit("ACC-001", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Credit amount must be positive");

        verify(accountRepository, never()).findByAccountId(any());
    }

    @Test
    @DisplayName("credit — returns empty when accountId not found")
    void credit_returnsEmptyWhenAccountNotFound() {
        when(accountRepository.findByAccountId("UNKNOWN")).thenReturn(Optional.empty());

        Optional<Account> result = accountService.credit("UNKNOWN", new BigDecimal("100.00"));

        assertThat(result).isEmpty();
        verify(accountRepository, never()).save(any());
    }

    // ── debit ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("debit — decreases balance by the debit amount")
    void debit_decreasesBalance() {
        Account existingAccount = buildAccount("ACC-001", new BigDecimal("1000.00"));
        when(accountRepository.findByAccountId("ACC-001")).thenReturn(Optional.of(existingAccount));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Account> result = accountService.debit("ACC-001", new BigDecimal("300.00"));

        assertThat(result).isPresent();
        assertThat(result.get().getBalance()).isEqualByComparingTo("700.00");
    }

    @Test
    @DisplayName("debit — rejects debit that would make balance negative")
    void debit_rejectsInsufficientBalance() {
        Account lowBalance = buildAccount("ACC-001", new BigDecimal("50.00"));
        when(accountRepository.findByAccountId("ACC-001")).thenReturn(Optional.of(lowBalance));

        assertThatThrownBy(() -> accountService.debit("ACC-001", new BigDecimal("100.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient balance");

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("debit — rejects zero or negative amount")
    void debit_rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> accountService.debit("ACC-001", new BigDecimal("-10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Debit amount must be positive");
    }

    // ── status transitions ────────────────────────────────────────────────────

    @Test
    @DisplayName("suspend — sets status to SUSPENDED")
    void suspend_setsStatusSuspended() {
        Account account = buildAccount("ACC-001", BigDecimal.ZERO);
        when(accountRepository.findByAccountId("ACC-001")).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Account> result = accountService.suspend("ACC-001");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(AccountStatus.SUSPENDED);

        ArgumentCaptor<Account> captured = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captured.capture());
        assertThat(captured.getValue().getStatus()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    @DisplayName("close — sets status to CLOSED")
    void close_setsStatusClosed() {
        Account account = buildAccount("ACC-001", BigDecimal.ZERO);
        when(accountRepository.findByAccountId("ACC-001")).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Account> result = accountService.close("ACC-001");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    @DisplayName("suspend — returns empty when account not found")
    void suspend_returnsEmptyWhenNotFound() {
        when(accountRepository.findByAccountId("MISSING")).thenReturn(Optional.empty());

        Optional<Account> result = accountService.suspend("MISSING");

        assertThat(result).isEmpty();
        verify(accountRepository, never()).save(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Account buildAccount(String accountId, BigDecimal balance) {
        return new Account(UUID.randomUUID(), accountId, "PART-001",
                balance, "USD", AccountStatus.ACTIVE);
    }
}
