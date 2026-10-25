package com.clearing.position.service;

import com.clearing.position.kafka.DividendPayoutPublisher;
import com.clearing.position.kafka.StockSplitPublisher;
import com.clearing.position.model.Position;
import com.clearing.position.repository.PositionRepository;
import com.clearing.shared.events.CorporateActionEvent;
import com.clearing.shared.events.DividendPayoutEvent;
import com.clearing.shared.events.StockSplitAppliedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class PositionCorporateActionService {

    private static final Logger log = LoggerFactory.getLogger(PositionCorporateActionService.class);
    private static final int BATCH_SIZE = 500;

    private final PositionRepository positionRepository;
    private final DividendPayoutPublisher dividendPayoutPublisher;
    private final StockSplitPublisher stockSplitPublisher;

    public PositionCorporateActionService(PositionRepository positionRepository,
                                          DividendPayoutPublisher dividendPayoutPublisher,
                                          StockSplitPublisher stockSplitPublisher) {
        this.positionRepository = positionRepository;
        this.dividendPayoutPublisher = dividendPayoutPublisher;
        this.stockSplitPublisher = stockSplitPublisher;
    }

    /**
     * Applies a stock split by multiplying the quantity of all positions for the given instrument.
     * Uses batching to prevent holding excessively long database locks.
     */
    @Transactional
    public void applyStockSplit(CorporateActionEvent event) {
        log.info("Starting STOCK_SPLIT for Instrument [{}]. Ratio: {}", event.instrumentId(), event.actionValue());
        
        int pageNumber = 0;
        Page<Position> page;

        do {
            page = positionRepository.findByInstrumentId(
                    event.instrumentId(), PageRequest.of(pageNumber, BATCH_SIZE));
            
            for (Position position : page.getContent()) {
                BigDecimal oldQuantity = position.getQuantity();
                BigDecimal newQuantity = oldQuantity.multiply(event.actionValue());
                position.setQuantity(newQuantity);

                StockSplitAppliedEvent splitEvent = new StockSplitAppliedEvent(
                        event.corporateActionId(),
                        position.getAccountId(),
                        event.instrumentId(),
                        oldQuantity,
                        newQuantity,
                        Instant.now()
                );
                stockSplitPublisher.publish(splitEvent);
            }

            positionRepository.saveAll(page.getContent());
            log.debug("Processed STOCK_SPLIT batch {}. Adjusted {} positions.", pageNumber, page.getNumberOfElements());
            
            pageNumber++;
        } while (page.hasNext());

        log.info("Finished STOCK_SPLIT for Instrument [{}].", event.instrumentId());
    }

    /**
     * Applies a dividend by calculating the cash equivalent for all positive positions
     * and publishing payout events to be consumed by the ledger (ReferenceDataService).
     */
    @Transactional
    public void applyDividend(CorporateActionEvent event) {
        log.info("Starting DIVIDEND for Instrument [{}]. Yield: {}", event.instrumentId(), event.actionValue());

        int pageNumber = 0;
        Page<Position> page;

        do {
            page = positionRepository.findByInstrumentId(
                    event.instrumentId(), PageRequest.of(pageNumber, BATCH_SIZE));

            for (Position position : page.getContent()) {
                if (position.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal payoutAmount = position.getQuantity().multiply(event.actionValue());
                    
                    DividendPayoutEvent payoutEvent = new DividendPayoutEvent(
                            event.corporateActionId(),
                            position.getAccountId(),
                            event.instrumentId(),
                            payoutAmount,
                            Instant.now()
                    );
                    dividendPayoutPublisher.publishPayout(payoutEvent);
                }
            }

            log.debug("Processed DIVIDEND batch {}. Evaluated {} positions.", pageNumber, page.getNumberOfElements());
            pageNumber++;
        } while (page.hasNext());

        log.info("Finished DIVIDEND for Instrument [{}].", event.instrumentId());
    }
}
