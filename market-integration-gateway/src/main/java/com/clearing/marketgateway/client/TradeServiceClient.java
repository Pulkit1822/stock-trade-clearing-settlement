package com.clearing.marketgateway.client;

import com.clearing.shared.dto.TradeRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP client for forwarding translated trade requests to TradeService.
 *
 * <p>Uses Spring 6.1 {@link RestClient} (blocking, no reactive dependency needed).
 * Base URL is injected via {@link com.clearing.marketgateway.config.TradeServiceClientConfig}.
 */
@Component
public class TradeServiceClient {

    private static final Logger log = LoggerFactory.getLogger(TradeServiceClient.class);
    private static final String TRADES_PATH = "/api/v1/trades";

    private final RestClient restClient;

    public TradeServiceClient(RestClient tradeServiceRestClient) {
        this.restClient = tradeServiceRestClient;
    }

    /**
     * Forwards a validated TradeRequest to TradeService POST /api/v1/trades.
     *
     * @param request  translated internal trade request
     * @return raw JSON node from TradeService response (trade entity)
     * @throws TradeServiceException if TradeService returns 4xx/5xx or is unreachable
     */
    public JsonNode submitTrade(TradeRequest request) {
        log.info("Forwarding trade to TradeService [instrument={}, buyer={}, seller={}]",
                request.instrumentId(), request.buyerAccountId(), request.sellerAccountId());

        try {
            JsonNode response = restClient.post()
                    .uri(TRADES_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);

            log.info("TradeService accepted trade [instrument={}]", request.instrumentId());
            return response;

        } catch (HttpClientErrorException e) {
            log.error("TradeService rejected trade [status={}, body={}]",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new TradeServiceException("Trade rejected by TradeService: " + e.getResponseBodyAsString(), e);

        } catch (HttpServerErrorException e) {
            log.error("TradeService internal error [status={}]", e.getStatusCode());
            throw new TradeServiceException("TradeService internal error", e);

        } catch (RestClientException e) {
            log.error("Failed to reach TradeService", e);
            throw new TradeServiceException("TradeService is unavailable", e);
        }
    }
}
