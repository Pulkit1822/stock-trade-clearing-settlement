package com.clearing.notification.controller;

import com.clearing.notification.model.Notification;
import com.clearing.notification.model.NotificationStatus;
import com.clearing.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = NotificationController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class}
)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Test
    @DisplayName("GET /notifications/trades/{tradeId} returns buyer and seller notifications")
    void getByTradeId_returns200WithNotifications() throws Exception {
        String tradeId = UUID.randomUUID().toString();
        List<Notification> notifications = List.of(
                buildNotification(tradeId, "BUYER-001", NotificationStatus.SENT),
                buildNotification(tradeId, "SELLER-001", NotificationStatus.SENT)
        );

        when(notificationService.findByTradeId(tradeId)).thenReturn(notifications);

        mockMvc.perform(get("/api/v1/notifications/trades/{tradeId}", tradeId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].accountId").value("BUYER-001"))
                .andExpect(jsonPath("$[1].accountId").value("SELLER-001"));
    }

    @Test
    @DisplayName("GET /notifications/trades/{tradeId} returns empty list when no notifications")
    void getByTradeId_noResults_returnsEmptyList() throws Exception {
        String tradeId = UUID.randomUUID().toString();
        when(notificationService.findByTradeId(tradeId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/notifications/trades/{tradeId}", tradeId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("GET /notifications/accounts/{accountId} returns account history")
    void getByAccountId_returns200WithNotifications() throws Exception {
        String accountId = "BUYER-001";
        String tradeId = UUID.randomUUID().toString();
        List<Notification> notifications = List.of(
                buildNotification(tradeId, accountId, NotificationStatus.SENT)
        );

        when(notificationService.findByAccountId(accountId)).thenReturn(notifications);

        mockMvc.perform(get("/api/v1/notifications/accounts/{accountId}", accountId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(accountId))
                .andExpect(jsonPath("$[0].status").value("SENT"));
    }

    @Test
    @DisplayName("GET /notifications?status=FAILED returns failed notifications")
    void getByStatus_failed_returnsList() throws Exception {
        String tradeId = UUID.randomUUID().toString();
        List<Notification> notifications = List.of(
                buildNotification(tradeId, "BUYER-001", NotificationStatus.FAILED)
        );

        when(notificationService.findByStatus(NotificationStatus.FAILED)).thenReturn(notifications);

        mockMvc.perform(get("/api/v1/notifications")
                        .param("status", "FAILED")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("FAILED"));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Notification buildNotification(String tradeId, String accountId, NotificationStatus status) {
        Notification n = new Notification(UUID.randomUUID(), tradeId, accountId,
                "Your trade " + tradeId + " has been settled.", status);
        if (status == NotificationStatus.SENT) {
            n.setSentAt(Instant.now());
        }
        return n;
    }
}
