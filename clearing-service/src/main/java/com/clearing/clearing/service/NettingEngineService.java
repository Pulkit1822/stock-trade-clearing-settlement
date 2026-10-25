package com.clearing.clearing.service;

import com.clearing.clearing.model.NetSettlementObligation;
import com.clearing.clearing.model.NettingGroup;
import com.clearing.clearing.model.Settlement;
import com.clearing.clearing.model.SettlementStatus;
import com.clearing.clearing.outbox.OutboxRepository;
import com.clearing.clearing.repository.NetSettlementObligationRepository;
import com.clearing.clearing.repository.NettingGroupRepository;
import com.clearing.shared.events.NetSettlementCompletedEvent;
import com.clearing.shared.outbox.OutboxEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class NettingEngineService {

    private static final Logger log = LoggerFactory.getLogger(NettingEngineService.class);

    private final ClearingService clearingService;
    private final NettingGroupRepository nettingGroupRepository;
    private final NetSettlementObligationRepository obligationRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public NettingEngineService(ClearingService clearingService,
                                NettingGroupRepository nettingGroupRepository,
                                NetSettlementObligationRepository obligationRepository,
                                OutboxRepository outboxRepository,
                                ObjectMapper objectMapper) {
        this.clearingService = clearingService;
        this.nettingGroupRepository = nettingGroupRepository;
        this.obligationRepository = obligationRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes the End-of-Day (EOD) Multilateral Netting cycle.
     */
    @Transactional
    public void executeEodNetting(String businessDate) {
        log.info("Starting EOD Multilateral Netting for business date: {}", businessDate);

        // 1. Create or get Netting Group
        NettingGroup nettingGroup = nettingGroupRepository.findByBusinessDate(businessDate)
                .orElseGet(() -> nettingGroupRepository.save(new NettingGroup(UUID.randomUUID(), businessDate)));

        // 2. Fetch all trades ready for netting
        List<Settlement> readyTrades = clearingService.findByStatus(SettlementStatus.READY_FOR_NETTING);
        if (readyTrades.isEmpty()) {
            log.info("No trades ready for netting for business date: {}", businessDate);
            return;
        }

        log.info("Found {} trades ready for netting.", readyTrades.size());

        // 3. Aggregate net positions (account + instrument)
        // Key: accountId + "|" + instrumentId
        Map<String, NetPosition> netPositions = new HashMap<>();

        for (Settlement trade : readyTrades) {
            String instrumentId = trade.getInstrumentId();
            long qty = trade.getQuantity();
            BigDecimal price = trade.getPrice();
            BigDecimal totalCash = price.multiply(BigDecimal.valueOf(qty));

            // Buyer side: receives shares (+qty), pays cash (-totalCash)
            String buyerKey = trade.getBuyerAccountId() + "|" + instrumentId;
            netPositions.computeIfAbsent(buyerKey, k -> new NetPosition(trade.getBuyerAccountId(), instrumentId))
                    .add(qty, totalCash.negate());

            // Seller side: delivers shares (-qty), receives cash (+totalCash)
            String sellerKey = trade.getSellerAccountId() + "|" + instrumentId;
            netPositions.computeIfAbsent(sellerKey, k -> new NetPosition(trade.getSellerAccountId(), instrumentId))
                    .add(-qty, totalCash);

            // Mark trade as netted
            trade.setStatus(SettlementStatus.NETTED);
            trade.setSettledAt(Instant.now());

            com.clearing.shared.events.SettlementCompletedEvent tradeEvent = new com.clearing.shared.events.SettlementCompletedEvent(
                    trade.getTradeId(),
                    trade.getBuyerAccountId(),
                    trade.getSellerAccountId(),
                    trade.getSettledAt()
            );

            try {
                outboxRepository.save(new OutboxEvent(
                        "Settlement", trade.getTradeId(), "SettlementCompletedEvent", objectMapper.writeValueAsString(tradeEvent)
                ));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize SettlementCompletedEvent for Outbox", e);
                throw new RuntimeException("Failed to serialize SettlementCompletedEvent for Outbox", e);
            }
        }

        // 4. Create obligations and publish events
        for (NetPosition pos : netPositions.values()) {
            // Even if perfectly flat (0 shares, $0 cash), we might want to record it for audit.
            NetSettlementObligation obligation = new NetSettlementObligation(
                    UUID.randomUUID(),
                    nettingGroup,
                    pos.accountId,
                    pos.instrumentId,
                    pos.netQuantity,
                    pos.netCash
            );
            obligationRepository.save(obligation);

            NetSettlementCompletedEvent event = new NetSettlementCompletedEvent(
                    nettingGroup.getId().toString(),
                    businessDate,
                    pos.accountId,
                    pos.instrumentId,
                    pos.netQuantity,
                    pos.netCash,
                    Instant.now()
            );

            try {
                outboxRepository.save(new OutboxEvent(
                        "NetSettlement", pos.accountId, "NetSettlementCompletedEvent", objectMapper.writeValueAsString(event)
                ));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize NetSettlementCompletedEvent", e);
                throw new RuntimeException("Failed to serialize NetSettlementCompletedEvent", e);
            }
        }

        log.info("EOD Multilateral Netting completed. Processed {} accounts with obligations.", netPositions.size());
    }

    private static class NetPosition {
        final String accountId;
        final String instrumentId;
        long netQuantity = 0;
        BigDecimal netCash = BigDecimal.ZERO;

        NetPosition(String accountId, String instrumentId) {
            this.accountId = accountId;
            this.instrumentId = instrumentId;
        }

        void add(long qty, BigDecimal cash) {
            this.netQuantity += qty;
            this.netCash = this.netCash.add(cash);
        }
    }
}
