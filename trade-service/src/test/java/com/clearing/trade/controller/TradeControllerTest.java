package com.clearing.trade.controller;

import com.clearing.shared.dto.TradeRequest;
import com.clearing.trade.model.Trade;
import com.clearing.trade.model.TradeStatus;
import com.clearing.trade.service.TradeProcessingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TradeController.class, 
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class}
)
class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TradeProcessingService tradeProcessingService;

    @Test
    void shouldReturn201CreatedWhenValidTradeRequestSubmitted() throws Exception {
        // Arrange
        TradeRequest request = new TradeRequest("AAPL", "BUYER123", "SELLER456", 100, new BigDecimal("150.50"));
        
        Trade mockedTrade = new Trade(
                UUID.randomUUID(), "BUYER123", "SELLER456", "AAPL", 
                new BigDecimal("100"), new BigDecimal("150.50"), 
                Instant.now(), TradeStatus.SUBMITTED
        );
        Mockito.when(tradeProcessingService.processTradeRequest(any(TradeRequest.class))).thenReturn(mockedTrade);

        // Act & Assert
        mockMvc.perform(post("/api/v1/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instrumentId").value("AAPL"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void shouldReturn400BadRequestWhenParametersMissing() throws Exception {
        // Arrange - empty payload or zero quantity triggers @Valid
        TradeRequest request = new TradeRequest("AAPL", "BUYER123", "SELLER456", 0, new BigDecimal("150.50"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
