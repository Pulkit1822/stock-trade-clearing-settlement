package com.clearing.refdata.controller;

import com.clearing.refdata.model.Account;
import com.clearing.refdata.model.AccountStatus;
import com.clearing.refdata.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST controller for trading account management.
 *
 * <p>Base path: {@code /accounts}
 *
 * <p>Routes registered in api-gateway:
 * <pre>
 *   GET  /accounts                    → list all accounts (optionally filtered by status)
 *   GET  /accounts/{accountId}        → get account by business ID
 *   GET  /accounts/participant/{pid}  → list all accounts for a participant
 *   POST /accounts                    → create new account
 *   PATCH /accounts/{accountId}/credit  → add cash to balance
 *   PATCH /accounts/{accountId}/debit   → subtract cash from balance
 *   PATCH /accounts/{accountId}/suspend → suspend account
 *   PATCH /accounts/{accountId}/close   → close account
 * </pre>
 */
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** GET /accounts — list all or filter by status. */
    @GetMapping
    public ResponseEntity<List<Account>> getAll(
            @RequestParam(required = false) AccountStatus status) {
        log.info("GET /accounts status={}", status);
        List<Account> result = status != null
                ? accountService.findByStatus(status)
                : accountService.findAll();
        return ResponseEntity.ok(result);
    }

    /** GET /accounts/{accountId} — get a single account by business ID. */
    @GetMapping("/{accountId}")
    public ResponseEntity<Account> getByAccountId(@PathVariable String accountId) {
        log.info("GET /accounts/{}", accountId);
        return accountService.findByAccountId(accountId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** GET /accounts/participant/{participantId} — list all accounts for a participant. */
    @GetMapping("/participant/{participantId}")
    public ResponseEntity<List<Account>> getByParticipant(@PathVariable String participantId) {
        log.info("GET /accounts/participant/{}", participantId);
        return ResponseEntity.ok(accountService.findByParticipantId(participantId));
    }

    /** POST /accounts — create a new account with an initial cash balance. */
    @PostMapping
    public ResponseEntity<Account> create(
            @RequestParam String accountId,
            @RequestParam String participantId,
            @RequestParam(defaultValue = "0") BigDecimal initialBalance,
            @RequestParam(defaultValue = "USD") String currency) {
        log.info("POST /accounts accountId={} participantId={}", accountId, participantId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.create(accountId, participantId, initialBalance, currency));
    }

    /** PATCH /accounts/{accountId}/credit — add cash to account balance. */
    @PatchMapping("/{accountId}/credit")
    public ResponseEntity<Account> credit(
            @PathVariable String accountId,
            @RequestParam BigDecimal amount) {
        log.info("PATCH /accounts/{}/credit amount={}", accountId, amount);
        return accountService.credit(accountId, amount)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** PATCH /accounts/{accountId}/debit — subtract cash from account balance. */
    @PatchMapping("/{accountId}/debit")
    public ResponseEntity<Account> debit(
            @PathVariable String accountId,
            @RequestParam BigDecimal amount) {
        log.info("PATCH /accounts/{}/debit amount={}", accountId, amount);
        return accountService.debit(accountId, amount)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** PATCH /accounts/{accountId}/suspend — suspend account (no new trades). */
    @PatchMapping("/{accountId}/suspend")
    public ResponseEntity<Account> suspend(@PathVariable String accountId) {
        log.info("PATCH /accounts/{}/suspend", accountId);
        return accountService.suspend(accountId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** PATCH /accounts/{accountId}/close — permanently close account. */
    @PatchMapping("/{accountId}/close")
    public ResponseEntity<Account> close(@PathVariable String accountId) {
        log.info("PATCH /accounts/{}/close", accountId);
        return accountService.close(accountId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
