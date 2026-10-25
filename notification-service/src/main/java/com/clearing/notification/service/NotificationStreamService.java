package com.clearing.notification.service;

import com.clearing.notification.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationStreamService {

    private static final Logger log = LoggerFactory.getLogger(NotificationStreamService.class);
    private static final long TIMEOUT_MS = 3600000L; // 1 hour timeout

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * Registers a new SseEmitter for a given account.
     *
     * @param accountId the account ID to register the emitter for
     * @return the configured SseEmitter
     */
    public SseEmitter createEmitter(String accountId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        
        emitters.computeIfAbsent(accountId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        
        emitter.onCompletion(() -> removeEmitter(accountId, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            removeEmitter(accountId, emitter);
        });
        emitter.onError(e -> {
            log.error("SSE Error for account [{}]: {}", accountId, e.getMessage());
            emitter.completeWithError(e);
            removeEmitter(accountId, emitter);
        });

        log.info("New SSE connection established for account [{}]. Total connections for account: {}", 
                 accountId, emitters.get(accountId).size());

        // Send an initial connection event
        try {
            emitter.send(SseEmitter.event().name("CONNECT").data("Connected successfully to notification stream"));
        } catch (IOException e) {
            emitter.completeWithError(e);
            removeEmitter(accountId, emitter);
        }

        return emitter;
    }

    /**
     * Pushes a notification to all active streams for the specified account.
     *
     * @param accountId the account ID to notify
     * @param notification the notification payload
     */
    public void pushNotification(String accountId, Notification notification) {
        List<SseEmitter> accountEmitters = emitters.get(accountId);
        if (accountEmitters == null || accountEmitters.isEmpty()) {
            return;
        }

        log.debug("Pushing notification to {} active streams for account [{}]", accountEmitters.size(), accountId);
        
        for (SseEmitter emitter : accountEmitters) {
            try {
                emitter.send(SseEmitter.event().name("NOTIFICATION").data(notification));
            } catch (IOException e) {
                log.warn("Failed to push notification to emitter for account [{}]. Marking for removal.", accountId);
                emitter.completeWithError(e);
                removeEmitter(accountId, emitter);
            }
        }
    }

    private void removeEmitter(String accountId, SseEmitter emitter) {
        List<SseEmitter> accountEmitters = emitters.get(accountId);
        if (accountEmitters != null) {
            accountEmitters.remove(emitter);
            if (accountEmitters.isEmpty()) {
                emitters.remove(accountId);
            }
        }
        log.info("Removed SSE connection for account [{}]", accountId);
    }

    /**
     * Sends a periodic heartbeat to prevent proxies/gateways from dropping idle connections.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeats() {
        if (emitters.isEmpty()) {
            return;
        }
        
        int count = 0;
        for (Map.Entry<String, List<SseEmitter>> entry : emitters.entrySet()) {
            String accountId = entry.getKey();
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    // Send a simple comment to keep the connection alive
                    emitter.send(SseEmitter.event().comment("ping"));
                    count++;
                } catch (IOException e) {
                    emitter.completeWithError(e);
                    removeEmitter(accountId, emitter);
                }
            }
        }
        log.trace("Sent heartbeat to {} active SSE connections", count);
    }
}
