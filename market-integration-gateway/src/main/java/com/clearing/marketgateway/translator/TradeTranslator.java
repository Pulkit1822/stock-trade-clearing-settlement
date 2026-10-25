package com.clearing.marketgateway.translator;

import com.clearing.marketgateway.dto.ExternalTradeRequest;
import com.clearing.shared.dto.TradeRequest;
import org.springframework.stereotype.Component;

/**
 * Translates an external broker trade payload into the internal
 * {@link TradeRequest} DTO expected by TradeService.
 *
 * <p>Field mapping:
 * <pre>
 *   ExternalTradeRequest          →   TradeRequest
 *   ticker                        →   instrumentId
 *   buyerAccount                  →   buyerAccountId
 *   sellerAccount                 →   sellerAccountId
 *   quantity                      →   quantity
 *   pricePerShare                 →   price
 * </pre>
 *
 * <p>Fields not forwarded in v1: brokerTradeId, tradeCurrency, executionTimestamp.
 * These are logged at the gateway for audit purposes.
 */
@Component
public class TradeTranslator {

    public TradeRequest toInternalRequest(ExternalTradeRequest external) {
        return new TradeRequest(
                external.ticker(),
                external.buyerAccount(),
                external.sellerAccount(),
                external.quantity(),
                external.pricePerShare()
        );
    }
}
