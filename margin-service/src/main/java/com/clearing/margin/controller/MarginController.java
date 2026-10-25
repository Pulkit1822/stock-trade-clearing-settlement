package com.clearing.margin.controller;

import com.clearing.margin.model.CollateralAccount;
import com.clearing.margin.model.MarginCall;
import com.clearing.margin.model.MarginCallStatus;
import com.clearing.margin.model.MarginRequirement;
import com.clearing.margin.repository.CollateralAccountRepository;
import com.clearing.margin.repository.MarginCallRepository;
import com.clearing.margin.repository.MarginRequirementRepository;
import com.clearing.margin.service.MarginCalculationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/margin")
public class MarginController {

    private final MarginCalculationService marginCalculationService;
    private final CollateralAccountRepository collateralAccountRepository;
    private final MarginRequirementRepository marginRequirementRepository;
    private final MarginCallRepository marginCallRepository;

    public MarginController(MarginCalculationService marginCalculationService,
                            CollateralAccountRepository collateralAccountRepository,
                            MarginRequirementRepository marginRequirementRepository,
                            MarginCallRepository marginCallRepository) {
        this.marginCalculationService = marginCalculationService;
        this.collateralAccountRepository = collateralAccountRepository;
        this.marginRequirementRepository = marginRequirementRepository;
        this.marginCallRepository = marginCallRepository;
    }

    @PostMapping("/collateral/{accountId}/deposit")
    public ResponseEntity<Object> depositCollateral(@PathVariable String accountId, @RequestParam BigDecimal amount) {
        try {
            marginCalculationService.depositCollateral(accountId, amount);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "accountId", accountId,
                    "depositedAmount", amount
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status/{accountId}")
    public ResponseEntity<Object> getMarginStatus(@PathVariable String accountId) {
        BigDecimal collateral = collateralAccountRepository.findByAccountId(accountId)
                .map(CollateralAccount::getBalance)
                .orElse(BigDecimal.ZERO);

        BigDecimal required = marginRequirementRepository.findByAccountId(accountId)
                .map(MarginRequirement::getRequiredMargin)
                .orElse(BigDecimal.ZERO);

        List<MarginCall> openCalls = marginCallRepository.findByAccountIdAndStatus(accountId, MarginCallStatus.OPEN);

        return ResponseEntity.ok(Map.of(
                "accountId", accountId,
                "availableCollateral", collateral,
                "requiredMargin", required,
                "hasOpenMarginCall", !openCalls.isEmpty(),
                "deficit", openCalls.isEmpty() ? BigDecimal.ZERO : openCalls.get(0).getDeficitAmount()
        ));
    }
}
