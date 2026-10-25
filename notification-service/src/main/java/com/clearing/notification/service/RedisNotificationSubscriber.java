package com.clearing.notification.service;

import com.clearing.notification.model.Notification;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class RedisNotificationSubscriber {

    private static final Logger log = LoggerFactory.getLogger(RedisNotificationSubscriber.class);

    private final NotificationStreamService streamService;
    private final ObjectMapper objectMapper;

    public RedisNotificationSubscriber(NotificationStreamService streamService, ObjectMapper objectMapper) {
        this.streamService = streamService;
        this.objectMapper = objectMapper;
    }

    public void onMessage(String message) {
        try {
            // The message might be serialized as a JSON string containing the JSON string
            // We should use ObjectMapper to read the actual Notification object.
            // Spring Data Redis with GenericJackson2JsonRedisSerializer might serialize objects directly,
            // but the payload comes in as a string here because MessageListenerAdapter passes the raw body
            // or the default deserialized object. Let's assume it passes the string.
            // If we use GenericJackson2JsonRedisSerializer, the string represents a JSON string.
            Notification notification = objectMapper.readValue(message, Notification.class);
            log.info("Received notification from Redis Pub/Sub for account: {}", notification.getAccountId());
            streamService.pushNotification(notification.getAccountId(), notification);
        } catch (IOException e) {
            log.error("Failed to deserialize notification from Redis Pub/Sub", e);
        }
    }
}
