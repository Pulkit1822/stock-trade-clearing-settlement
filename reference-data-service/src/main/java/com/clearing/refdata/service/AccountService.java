package com.clearing.refdata.service;

import com.clearing.refdata.model.Account;
import com.clearing.refdata.model.AccountStatus;
import com.clearing.refdata.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for managing trading accounts within the ReferenceData domain.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Create accounts for a participant with an initial cash balance.</li>
 *   <li>Credit/debit cash balance (for settlement funding).</li>
 *   <li>Suspend or close accounts.</li>
 *   <li>Query accounts by participant or status.</li>
 * </ul>
 *
 * <p>Note: Share quantity changes are owned by position-service. This service
 * only manages the <em>cash</em> dimension of an account.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    public Optional<Account> findByAccountId(String accountId) {
        return accountRepository.findByAccountId(accountId);
    }

    public List<Account> findByParticipantId(String participantId) {
        return accountRepository.findByParticipantId(participantId);
    }

    public List<Account> findByStatus(AccountStatus status) {
        return accountRepository.findByStatus(status);
    }

    public List<Account> findAll() {
        return accountRepository.findAll();
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    /**
     * Creates a new trading account for the given participant.
     *
     * @param accountId     Business-level identifier (e.g. "ACC-001").
     * @param participantId The owning participant's business ID.
     * @param initialBalance Starting cash balance. Must be ≥ 0.
     * @param currency      ISO 4217 currency code (e.g. "USD").
     * @return The persisted Account entity.
     * @throws IllegalArgumentException if initialBalance is negative.
     */
    @Transactional
    public Account create(String accountId, String participantId,
                          BigDecimal initialBalance, String currency) {
        if (initialBalance.compareTo(ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Initial balance cannot be negative for accountId [" + accountId + "]");
        }

        Account account = new Account(
                UUID.randomUUID(), accountId, participantId,
                initialBalance, currency, AccountStatus.ACTIVE);

        Account saved = accountRepository.save(account);
        log.info("Account created [accountId={}, participantId={}, balance={} {}]",
                accountId, participantId, initialBalance, currency);
        return saved;
    }

    /**
     * Credits (adds) an amount to the account's cash balance.
     *
     * @param accountId   Business identifier of the account.
     * @param creditAmount Amount to add. Must be > 0.
     * @return Updated Account, or empty if accountId not found.
     * @throws IllegalArgumentException if creditAmount ≤ 0.
     */
    @Transactional
    public Optional<Account> credit(String accountId, BigDecimal creditAmount) {
        if (creditAmount.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Credit amount must be positive [accountId=" + accountId + ", amount=" + creditAmount + "]");
        }

        return accountRepository.findByAccountId(accountId).map(account -> {
            BigDecimal newBalance = account.getBalance().add(creditAmount);
            account.setBalance(newBalance);
            Account saved = accountRepository.save(account);
            log.info("Account credited [accountId={}, amount={}, newBalance={}]",
                    accountId, creditAmount, newBalance);
            return saved;
        });
    }

    /**
     * Debits (subtracts) an amount from the account's cash balance.
     *
     * @param accountId   Business identifier of the account.
     * @param debitAmount Amount to subtract. Must be > 0.
     * @return Updated Account, or empty if accountId not found.
     * @throws IllegalArgumentException if debitAmount ≤ 0 or would make balance negative.
     */
    @Transactional
    public Optional<Account> debit(String accountId, BigDecimal debitAmount) {
        if (debitAmount.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Debit amount must be positive [accountId=" + accountId + ", amount=" + debitAmount + "]");
        }

        return accountRepository.findByAccountId(accountId).map(account -> {
            if (account.getBalance().compareTo(debitAmount) < 0) {
                throw new IllegalStateException(
                        "Insufficient balance for debit [accountId=" + accountId
                        + ", balance=" + account.getBalance()
                        + ", debitAmount=" + debitAmount + "]");
            }
            BigDecimal newBalance = account.getBalance().subtract(debitAmount);
            account.setBalance(newBalance);
            Account saved = accountRepository.save(account);
            log.info("Account debited [accountId={}, amount={}, newBalance={}]",
                    accountId, debitAmount, newBalance);
            return saved;
        });
    }

    /**
     * Suspends an account — no new trades allowed while suspended.
     */
    @Transactional
    public Optional<Account> suspend(String accountId) {
        return accountRepository.findByAccountId(accountId).map(account -> {
            account.setStatus(AccountStatus.SUSPENDED);
            Account saved = accountRepository.save(account);
            log.info("Account suspended [accountId={}]", accountId);
            return saved;
        });
    }

    /**
     * Permanently closes an account.
     */
    @Transactional
    public Optional<Account> close(String accountId) {
        return accountRepository.findByAccountId(accountId).map(account -> {
            account.setStatus(AccountStatus.CLOSED);
            Account saved = accountRepository.save(account);
            log.info("Account closed [accountId={}]", accountId);
            return saved;
        });
    }
}
