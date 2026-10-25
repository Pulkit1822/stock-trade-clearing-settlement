package com.clearing.margin.service;

import com.clearing.margin.model.CollateralAccount;
import com.clearing.margin.model.MarginCall;
import com.clearing.margin.model.MarginCallStatus;
import com.clearing.margin.model.MarginRequirement;
import com.clearing.margin.outbox.OutboxRepository;
import com.clearing.margin.repository.CollateralAccountRepository;
import com.clearing.margin.repository.MarginCallRepository;
import com.clearing.margin.repository.MarginRequirementRepository;
import com.clearing.shared.events.MarginCallEvent;
import com.clearing.shared.outbox.OutboxEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MarginCalculationService {

    private static final Logger log = LoggerFactory.getLogger(MarginCalculationService.class);

    // Mock constants for MVP
    private static final BigDecimal MOCK_PRICE = new BigDecimal("100.00");
    private static final BigDecimal HAIRCUT = new BigDecimal("0.15");

    private final MarginRequirementRepository marginRequirementRepository;
    private final CollateralAccountRepository collateralAccountRepository;
    private final MarginCallRepository marginCallRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public MarginCalculationService(MarginRequirementRepository marginRequirementRepository,
                                    CollateralAccountRepository collateralAccountRepository,
                                    MarginCallRepository marginCallRepository,
                                    OutboxRepository outboxRepository,
                                    ObjectMapper objectMapper) {
        this.marginRequirementRepository = marginRequirementRepository;
        this.collateralAccountRepository = collateralAccountRepository;
        this.marginCallRepository = marginCallRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void evaluateMarginRequirement(String accountId, String instrumentId, int newQuantity) {
        log.info("Evaluating margin requirement for account [{}] based on new position of {} for instrument [{}]",
                 accountId, newQuantity, instrumentId);

        // 1. Calculate Required Margin
        // Simple formula: abs(quantity) * mock_price * haircut
        BigDecimal absQty = new BigDecimal(Math.abs(newQuantity));
        BigDecimal requiredMargin = absQty.multiply(MOCK_PRICE).multiply(HAIRCUT);

        MarginRequirement requirement = marginRequirementRepository.findByAccountId(accountId)
                .orElseGet(() -> new MarginRequirement(UUID.randomUUID(), accountId));
        requirement.setRequiredMargin(requiredMargin);
        requirement.setLastCalculatedAt(Instant.now());
        marginRequirementRepository.save(requirement);

        // 2. Fetch Collateral
        CollateralAccount collateral = collateralAccountRepository.findByAccountId(accountId)
                .orElseGet(() -> collateralAccountRepository.save(new CollateralAccount(UUID.randomUUID(), accountId)));

        BigDecimal availableCollateral = collateral.getBalance();

        log.info("Account [{}]: Required Margin = {}, Available Collateral = {}",
                 accountId, requiredMargin, availableCollateral);

        // 3. Compare and potentially issue a Margin Call
        if (requiredMargin.compareTo(availableCollateral) > 0) {
            BigDecimal deficit = requiredMargin.subtract(availableCollateral);
            issueMarginCall(accountId, requiredMargin, availableCollateral, deficit);
        } else {
            // Check if there are open margin calls that can now be closed
            resolveOpenMarginCalls(accountId, availableCollateral, requiredMargin);
        }
    }

    @Transactional
    public void depositCollateral(String accountId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }

        CollateralAccount collateral = collateralAccountRepository.findByAccountId(accountId)
                .orElseGet(() -> new CollateralAccount(UUID.randomUUID(), accountId));
        
        collateral.setBalance(collateral.getBalance().add(amount));
        collateral.setUpdatedAt(Instant.now());
        collateralAccountRepository.save(collateral);

        log.info("Deposited {} to account [{}]. New balance: {}", amount, accountId, collateral.getBalance());

        // Re-evaluate outstanding margin calls
        MarginRequirement requirement = marginRequirementRepository.findByAccountId(accountId).orElse(null);
        BigDecimal requiredMargin = requirement != null ? requirement.getRequiredMargin() : BigDecimal.ZERO;
        
        resolveOpenMarginCalls(accountId, collateral.getBalance(), requiredMargin);
    }

    private void issueMarginCall(String accountId, BigDecimal requiredMargin, BigDecimal availableCollateral, BigDecimal deficit) {
        // Check if there is already an open margin call
        List<MarginCall> openCalls = marginCallRepository.findByAccountIdAndStatus(accountId, MarginCallStatus.OPEN);
        if (!openCalls.isEmpty()) {
            log.info("Account [{}] already has an open margin call. Updating or ignoring.", accountId);
            // We could update the existing call's deficit, but for MVP we will just let the first one stand until resolved
            return;
        }

        MarginCall call = new MarginCall(UUID.randomUUID(), accountId, deficit);
        marginCallRepository.save(call);
        
        log.warn("Issuing MARGIN CALL for account [{}]. Deficit: {}", accountId, deficit);

        MarginCallEvent event = new MarginCallEvent(
                call.getId().toString(),
                accountId,
                requiredMargin,
                availableCollateral,
                deficit,
                call.getIssuedAt()
        );

        try {
            outboxRepository.save(new OutboxEvent(
                    "MarginCall", accountId, "MarginCallEvent", objectMapper.writeValueAsString(event)
            ));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize MarginCallEvent", e);
            throw new RuntimeException("Failed to serialize MarginCallEvent", e);
        }
    }

    private void resolveOpenMarginCalls(String accountId, BigDecimal availableCollateral, BigDecimal requiredMargin) {
        if (availableCollateral.compareTo(requiredMargin) >= 0) {
            List<MarginCall> openCalls = marginCallRepository.findByAccountIdAndStatus(accountId, MarginCallStatus.OPEN);
            for (MarginCall call : openCalls) {
                call.setStatus(MarginCallStatus.RESOLVED);
                call.setResolvedAt(Instant.now());
                marginCallRepository.save(call);
                log.info("Resolved margin call [{}] for account [{}]", call.getId(), accountId);
            }
        }
    }
}
