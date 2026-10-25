package com.clearing.notification.service;

import com.clearing.notification.kafka.NotificationEventPublisher;
import com.clearing.notification.model.Notification;
import com.clearing.notification.model.NotificationStatus;
import com.clearing.notification.repository.NotificationRepository;
import com.clearing.shared.events.NotificationEvent;
import com.clearing.shared.events.SettlementCompletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationEventPublisher notificationEventPublisher;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("settlement-completed creates two notifications — one per account side")
    void handleSettlementCompleted_createsTwoNotifications() {
        // Arrange
        String tradeId = UUID.randomUUID().toString();
        SettlementCompletedEvent event = new SettlementCompletedEvent(
                tradeId, "BUYER-001", "SELLER-001", Instant.now());

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.handleSettlementCompleted(event);

        // Assert — two saves per notification (PENDING then SENT) = 4 total
        verify(notificationRepository, times(4)).save(any(Notification.class));
    }

    @Test
    @DisplayName("Buyer notification contains correct accountId and tradeId")
    void handleSettlementCompleted_buyerNotification_hasCorrectFields() {
        // Arrange
        String tradeId = UUID.randomUUID().toString();
        SettlementCompletedEvent event = new SettlementCompletedEvent(
                tradeId, "BUYER-001", "SELLER-001", Instant.now());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        when(notificationRepository.save(captor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.handleSettlementCompleted(event);

        // Assert — first save is buyer PENDING notification
        Notification buyerNotification = captor.getAllValues().get(0);
        assertEquals("BUYER-001", buyerNotification.getAccountId());
        assertEquals(tradeId, buyerNotification.getTradeId());
        assertTrue(buyerNotification.getMessage().contains(tradeId));
    }

    @Test
    @DisplayName("Seller notification contains correct accountId and tradeId")
    void handleSettlementCompleted_sellerNotification_hasCorrectFields() {
        // Arrange
        String tradeId = UUID.randomUUID().toString();
        SettlementCompletedEvent event = new SettlementCompletedEvent(
                tradeId, "BUYER-001", "SELLER-001", Instant.now());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        when(notificationRepository.save(captor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.handleSettlementCompleted(event);

        // Assert — third save is seller PENDING notification
        Notification sellerNotification = captor.getAllValues().get(2);
        assertEquals("SELLER-001", sellerNotification.getAccountId());
        assertEquals(tradeId, sellerNotification.getTradeId());
        assertTrue(sellerNotification.getMessage().contains(tradeId));
    }

    @Test
    @DisplayName("Two NotificationEvents are published — one per account")
    void handleSettlementCompleted_publishesTwoNotificationEvents() {
        // Arrange
        String tradeId = UUID.randomUUID().toString();
        SettlementCompletedEvent event = new SettlementCompletedEvent(
                tradeId, "BUYER-001", "SELLER-001", Instant.now());

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.handleSettlementCompleted(event);

        // Assert — one event per account
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventPublisher, times(2)).publish(captor.capture());

        List<NotificationEvent> published = captor.getAllValues();
        assertEquals("BUYER-001", published.get(0).accountId());
        assertEquals("SELLER-001", published.get(1).accountId());
        assertEquals(tradeId, published.get(0).tradeId());
        assertEquals(tradeId, published.get(1).tradeId());
    }

    @Test
    @DisplayName("Notifications are marked SENT after dispatch")
    void handleSettlementCompleted_notificationsMarkedSent() {
        // Arrange
        String tradeId = UUID.randomUUID().toString();
        SettlementCompletedEvent event = new SettlementCompletedEvent(
                tradeId, "BUYER-001", "SELLER-001", Instant.now());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        when(notificationRepository.save(captor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.handleSettlementCompleted(event);

        // Assert — second save (index 1) is buyer marked SENT, fourth (index 3) is seller
        assertEquals(NotificationStatus.SENT, captor.getAllValues().get(1).getStatus());
        assertNotNull(captor.getAllValues().get(1).getSentAt());
        assertEquals(NotificationStatus.SENT, captor.getAllValues().get(3).getStatus());
        assertNotNull(captor.getAllValues().get(3).getSentAt());
    }
}
