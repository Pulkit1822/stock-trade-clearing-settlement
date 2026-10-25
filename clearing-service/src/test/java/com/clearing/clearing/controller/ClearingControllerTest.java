package com.clearing.clearing.controller;

import com.clearing.clearing.model.Settlement;
import com.clearing.clearing.model.SettlementStatus;
import com.clearing.clearing.service.ClearingService;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = ClearingController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class}
)
class ClearingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClearingService clearingService;

    @Test
    @DisplayName("GET /settlements/{tradeId} returns 200 with settlement when found")
    void getByTradeId_found_returns200() throws Exception {
        String tradeId = UUID.randomUUID().toString();
        Settlement settlement = buildSettlement(tradeId, SettlementStatus.SETTLED);

        when(clearingService.findByTradeId(tradeId)).thenReturn(Optional.of(settlement));

        mockMvc.perform(get("/api/v1/clearing/settlements/{tradeId}", tradeId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value(tradeId))
                .andExpect(jsonPath("$.status").value("SETTLED"));
    }

    @Test
    @DisplayName("GET /settlements/{tradeId} returns 404 when not found")
    void getByTradeId_notFound_returns404() throws Exception {
        String tradeId = UUID.randomUUID().toString();
        when(clearingService.findByTradeId(tradeId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/clearing/settlements/{tradeId}", tradeId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /settlements?status=PENDING returns list of pending settlements")
    void getByStatus_pending_returnsList() throws Exception {
        String tradeId = UUID.randomUUID().toString();
        Settlement settlement = buildSettlement(tradeId, SettlementStatus.PENDING);

        when(clearingService.findByStatus(SettlementStatus.PENDING)).thenReturn(List.of(settlement));

        mockMvc.perform(get("/api/v1/clearing/settlements")
                        .param("status", "PENDING")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tradeId").value(tradeId))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /settlements?status=PENDING returns empty list when none found")
    void getByStatus_noResults_returnsEmptyList() throws Exception {
        when(clearingService.findByStatus(SettlementStatus.PENDING)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/clearing/settlements")
                        .param("status", "PENDING")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("GET /accounts/{accountId}/settlements returns all settlements for account")
    void getByAccountId_found_returnsList() throws Exception {
        String accountId = "BUYER-001";
        String tradeId = UUID.randomUUID().toString();
        Settlement settlement = buildSettlement(tradeId, SettlementStatus.SETTLED);
        settlement.setBuyerAccountId(accountId);

        when(clearingService.findByAccountId(accountId)).thenReturn(List.of(settlement));

        mockMvc.perform(get("/api/v1/clearing/accounts/{accountId}/settlements", accountId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tradeId").value(tradeId))
                .andExpect(jsonPath("$[0].buyerAccountId").value(accountId));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Settlement buildSettlement(String tradeId, SettlementStatus status) {
        Settlement settlement = new Settlement(UUID.randomUUID(), tradeId, status);
        settlement.setBuyerAccountId("BUYER-001");
        settlement.setSellerAccountId("SELLER-001");
        settlement.setInstrumentId("AAPL");
        if (status == SettlementStatus.SETTLED) {
            settlement.setSettledAt(Instant.now());
        }
        return settlement;
    }
}
