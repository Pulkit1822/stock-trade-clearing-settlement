package com.clearing.notification.service;

import com.clearing.notification.kafka.NotificationEventPublisher;
import com.clearing.notification.model.Notification;
import com.clearing.notification.model.NotificationStatus;
import com.clearing.notification.model.ProcessedEvent;
import com.clearing.notification.repository.NotificationRepository;
import com.clearing.notification.repository.ProcessedEventRepository;
import com.clearing.shared.events.NotificationEvent;
import com.clearing.shared.events.SettlementCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationEventPublisher notificationEventPublisher;
    private final ProcessedEventRepository processedEventRepository;

    public NotificationService(NotificationRepository notificationRepository,
                                NotificationEventPublisher notificationEventPublisher,
                                ProcessedEventRepository processedEventRepository) {
        this.notificationRepository = notificationRepository;
        this.notificationEventPublisher = notificationEventPublisher;
        this.processedEventRepository = processedEventRepository;
    }

    /**
     * Handles a settlement-completed event from ClearingService.
     *
     * Creates and dispatches two notifications per trade:
     * - One for the buyer account
     * - One for the seller account
     *
     * Each notification is persisted with status SENT and a NotificationEvent
     * is published to the notification-events topic for audit/monitoring.
     */
    @Transactional
    public void handleSettlementCompleted(SettlementCompletedEvent event) {
        String idempotencyKey = "SETTLEMENT-" + event.tradeId();
        if (processedEventRepository.existsById(idempotencyKey)) {
            log.warn("Duplicate SettlementCompletedEvent received for tradeId [{}]. Skipping (idempotent).", event.tradeId());
            return;
        }
        processedEventRepository.save(new ProcessedEvent(idempotencyKey, Instant.now()));
        notify(event.tradeId(), event.buyerAccount(),
                String.format("Your trade %s has been settled. Shares have been credited to your account.",
                        event.tradeId()));

        notify(event.tradeId(), event.sellerAccount(),
                String.format("Your trade %s has been settled. Shares have been debited from your account.",
                        event.tradeId()));
    }

    private void notify(String tradeId, String accountId, String message) {
        Notification notification = new Notification(
                UUID.randomUUID(), tradeId, accountId, message, NotificationStatus.PENDING);

        notificationRepository.save(notification);

        // Simulate dispatch (email/webhook would be called here in production)
        log.info("DISPATCH → accountId=[{}] tradeId=[{}] message=[{}]", accountId, tradeId, message);

        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(Instant.now());
        notificationRepository.save(notification);

        notificationEventPublisher.publish(new NotificationEvent(accountId, tradeId, message, Instant.now()));
    }

    @Transactional
    public void handleDividendPayout(com.clearing.shared.events.DividendPayoutEvent event) {
        String idempotencyKey = "DIVIDEND-" + event.corporateActionId() + "-" + event.accountId();
        if (processedEventRepository.existsById(idempotencyKey)) {
            log.warn("Duplicate DividendPayoutEvent received for action [{}] account [{}]. Skipping (idempotent).", 
                     event.corporateActionId(), event.accountId());
            return;
        }
        processedEventRepository.save(new ProcessedEvent(idempotencyKey, Instant.now()));
        notify(event.corporateActionId(), event.accountId(),
                String.format("Dividend payout of $%s credited for your holdings of %s.",
                        event.amount().toPlainString(), event.instrumentId()));
    }

    @Transactional
    public void handleStockSplit(com.clearing.shared.events.StockSplitAppliedEvent event) {
        String idempotencyKey = "SPLIT-" + event.corporateActionId() + "-" + event.accountId();
        if (processedEventRepository.existsById(idempotencyKey)) {
            log.warn("Duplicate StockSplitAppliedEvent received for action [{}] account [{}]. Skipping (idempotent).", 
                     event.corporateActionId(), event.accountId());
            return;
        }
        processedEventRepository.save(new ProcessedEvent(idempotencyKey, Instant.now()));
        notify(event.corporateActionId(), event.accountId(),
                String.format("Stock split applied for %s. Your holdings changed from %s to %s.",
                        event.instrumentId(), event.oldQuantity().toPlainString(), event.newQuantity().toPlainString()));
    }

    public List<Notification> findByTradeId(String tradeId) {
        return notificationRepository.findByTradeId(tradeId);
    }

    public List<Notification> findByAccountId(String accountId) {
        return notificationRepository.findByAccountId(accountId);
    }

    public List<Notification> findByStatus(NotificationStatus status) {
        return notificationRepository.findByStatus(status);
    }
}
