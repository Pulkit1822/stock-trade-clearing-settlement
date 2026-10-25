package com.clearing.marketgateway.controller;

import com.clearing.marketgateway.client.TradeServiceClient;
import com.clearing.marketgateway.client.TradeServiceException;
import com.clearing.marketgateway.dto.ExternalTradeRequest;
import com.clearing.marketgateway.translator.TradeTranslator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpServerErrorException;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = MarketTradeController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class}
)
class MarketTradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TradeTranslator tradeTranslator;

    @MockBean
    private TradeServiceClient tradeServiceClient;

    @Test
    @DisplayName("POST /market/trades returns 201 with clearingTradeId on success")
    void submitTrade_valid_returns201WithClearingTradeId() throws Exception {
        ExternalTradeRequest request = buildRequest("AAPL", "B-001", "S-001", 100, "150.00");

        ObjectNode tradeServiceResponse = objectMapper.createObjectNode();
        tradeServiceResponse.put("id", "clearing-uuid-123");
        tradeServiceResponse.put("status", "SUBMITTED");

        when(tradeTranslator.toInternalRequest(any())).thenCallRealMethod();
        when(tradeServiceClient.submitTrade(any())).thenReturn(tradeServiceResponse);

        mockMvc.perform(post("/api/v1/market/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clearingTradeId").value("clearing-uuid-123"))
                .andExpect(jsonPath("$.brokerTradeId").value("BROKER-REF-001"))
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("POST /market/trades returns 502 when TradeService is unavailable")
    void submitTrade_tradeServiceUnavailable_returns502() throws Exception {
        ExternalTradeRequest request = buildRequest("AAPL", "B-001", "S-001", 100, "150.00");

        when(tradeTranslator.toInternalRequest(any())).thenCallRealMethod();
        when(tradeServiceClient.submitTrade(any()))
                .thenThrow(new TradeServiceException("TradeService is unavailable",
                        new HttpServerErrorException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)));

        mockMvc.perform(post("/api/v1/market/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway());
    }

    @Test
    @DisplayName("POST /market/trades returns 400 when ticker is missing")
    void submitTrade_missingTicker_returns400() throws Exception {
        ExternalTradeRequest request = new ExternalTradeRequest(
                "BROKER-REF-001", "", "B-001", "S-001",
                100, new BigDecimal("150.00"), "USD", Instant.now());

        mockMvc.perform(post("/api/v1/market/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /market/trades returns 400 when quantity is zero")
    void submitTrade_zeroQuantity_returns400() throws Exception {
        ExternalTradeRequest request = new ExternalTradeRequest(
                "BROKER-REF-001", "AAPL", "B-001", "S-001",
                0, new BigDecimal("150.00"), "USD", Instant.now());

        mockMvc.perform(post("/api/v1/market/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ExternalTradeRequest buildRequest(String ticker, String buyer, String seller,
                                               int quantity, String price) {
        return new ExternalTradeRequest(
                "BROKER-REF-001", ticker, buyer, seller,
                quantity, new BigDecimal(price), "USD", Instant.now());
    }
}
