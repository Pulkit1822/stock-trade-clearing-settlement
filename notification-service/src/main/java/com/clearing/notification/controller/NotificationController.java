package com.clearing.notification.controller;

import com.clearing.notification.model.Notification;
import com.clearing.notification.model.NotificationStatus;
import com.clearing.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.clearing.notification.service.NotificationStreamService;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;
    private final NotificationStreamService notificationStreamService;

    public NotificationController(NotificationService notificationService,
                                  NotificationStreamService notificationStreamService) {
        this.notificationService = notificationService;
        this.notificationStreamService = notificationStreamService;
    }

    /**
     * GET /api/v1/notifications/stream/{accountId}
     * Returns an SSE stream for real-time notifications.
     */
    @GetMapping(path = "/stream/{accountId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications(@PathVariable String accountId) {
        log.info("SSE Stream requested for account: {}", accountId);
        return notificationStreamService.createEmitter(accountId);
    }

    /**
     * GET /api/v1/notifications/trades/{tradeId}
     * Returns all notifications (buyer + seller) for a specific trade.
     */
    @GetMapping("/trades/{tradeId}")
    public ResponseEntity<List<Notification>> getByTradeId(@PathVariable String tradeId) {
        log.info("GET /api/v1/notifications/trades/{}", tradeId);
        return ResponseEntity.ok(notificationService.findByTradeId(tradeId));
    }

    /**
     * GET /api/v1/notifications/accounts/{accountId}
     * Returns full notification history for a participant account.
     */
    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<List<Notification>> getByAccountId(@PathVariable String accountId) {
        log.info("GET /api/v1/notifications/accounts/{}", accountId);
        return ResponseEntity.ok(notificationService.findByAccountId(accountId));
    }

    /**
     * GET /api/v1/notifications?status=FAILED
     * Returns all notifications in a given status — used for retry monitoring.
     */
    @GetMapping
    public ResponseEntity<List<Notification>> getByStatus(@RequestParam NotificationStatus status) {
        log.info("GET /api/v1/notifications?status={}", status);
        return ResponseEntity.ok(notificationService.findByStatus(status));
    }
}
