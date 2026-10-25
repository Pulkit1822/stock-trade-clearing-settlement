package com.clearing.shared.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Standardizes Kafka Error Handling across all microservices.
 * 
 * Configures the DefaultErrorHandler to retry failed messages 3 times 
 * (with a 1-second delay), after which the message is published to the 
 * `<original-topic>.DLT` (Dead Letter Topic).
 */
@Configuration
public class KafkaErrorHandlerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlerConfig.class);

    @Bean
    public DefaultErrorHandler defaultErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        log.info("Configuring DefaultErrorHandler with DeadLetterPublishingRecoverer (3 retries, 1s backoff)");
        
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        
        // Retry 3 times (4 total attempts) with 1000ms delay between retries
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);
        
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        
        // Add specific exceptions to NOT retry if needed, e.g. IllegalArgumentException
        // errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        
        return errorHandler;
    }
}
