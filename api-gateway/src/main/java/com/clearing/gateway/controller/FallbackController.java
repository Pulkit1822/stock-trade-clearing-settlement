package com.clearing.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/position")
    public ResponseEntity<Map<String, String>> positionFallback() {
        return buildErrorResponse("Position Service is currently unavailable. Please try again later.");
    }

    @RequestMapping("/clearing")
    public ResponseEntity<Map<String, String>> clearingFallback() {
        return buildErrorResponse("Clearing Service is currently unavailable.");
    }

    @RequestMapping("/market")
    public ResponseEntity<Map<String, String>> marketFallback() {
        return buildErrorResponse("Market Integration Gateway is currently unavailable.");
    }

    @RequestMapping("/refdata")
    public ResponseEntity<Map<String, String>> refDataFallback() {
        return buildErrorResponse("Reference Data Service is currently unavailable.");
    }

    @RequestMapping("/corporate-action")
    public ResponseEntity<Map<String, String>> corporateActionFallback() {
        return buildErrorResponse("Corporate Action Service is currently unavailable.");
    }

    @RequestMapping("/notification")
    public ResponseEntity<Map<String, String>> notificationFallback() {
        return buildErrorResponse("Notification Service is currently unavailable.");
    }

    @RequestMapping("/margin")
    public ResponseEntity<Map<String, String>> marginFallback() {
        return buildErrorResponse("Margin Service is currently unavailable.");
    }

    private ResponseEntity<Map<String, String>> buildErrorResponse(String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", message, "error", "Service Unavailable"));
    }
}
