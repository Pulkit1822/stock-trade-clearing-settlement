package com.clearing.marketgateway.translator;

import com.clearing.marketgateway.dto.ExternalTradeRequest;
import com.clearing.shared.dto.TradeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradeTranslatorTest {

    private final TradeTranslator tradeTranslator = new TradeTranslator();

    @Test
    @DisplayName("ticker maps to instrumentId")
    void translate_ticker_mapsToInstrumentId() {
        ExternalTradeRequest external = buildRequest("AAPL", "B-001", "S-001", 100, new BigDecimal("150.00"));

        TradeRequest internal = tradeTranslator.toInternalRequest(external);

        assertEquals("AAPL", internal.instrumentId());
    }

    @Test
    @DisplayName("buyerAccount maps to buyerAccountId")
    void translate_buyerAccount_mapsToBuyerAccountId() {
        ExternalTradeRequest external = buildRequest("AAPL", "BUYER-001", "S-001", 100, new BigDecimal("150.00"));

        TradeRequest internal = tradeTranslator.toInternalRequest(external);

        assertEquals("BUYER-001", internal.buyerAccountId());
    }

    @Test
    @DisplayName("sellerAccount maps to sellerAccountId")
    void translate_sellerAccount_mapsToSellerAccountId() {
        ExternalTradeRequest external = buildRequest("AAPL", "B-001", "SELLER-001", 100, new BigDecimal("150.00"));

        TradeRequest internal = tradeTranslator.toInternalRequest(external);

        assertEquals("SELLER-001", internal.sellerAccountId());
    }

    @Test
    @DisplayName("quantity is preserved exactly")
    void translate_quantity_preservedExactly() {
        ExternalTradeRequest external = buildRequest("AAPL", "B-001", "S-001", 250, new BigDecimal("150.00"));

        TradeRequest internal = tradeTranslator.toInternalRequest(external);

        assertEquals(250, internal.quantity());
    }

    @Test
    @DisplayName("pricePerShare maps to price")
    void translate_pricePerShare_mapsToPrice() {
        BigDecimal price = new BigDecimal("182.75");
        ExternalTradeRequest external = buildRequest("AAPL", "B-001", "S-001", 100, price);

        TradeRequest internal = tradeTranslator.toInternalRequest(external);

        assertEquals(price, internal.price());
    }

    @Test
    @DisplayName("full translation — all fields mapped correctly")
    void translate_fullRequest_allFieldsMappedCorrectly() {
        ExternalTradeRequest external = buildRequest("TSLA", "ACC-BUY", "ACC-SELL", 50, new BigDecimal("200.00"));

        TradeRequest internal = tradeTranslator.toInternalRequest(external);

        assertEquals("TSLA", internal.instrumentId());
        assertEquals("ACC-BUY", internal.buyerAccountId());
        assertEquals("ACC-SELL", internal.sellerAccountId());
        assertEquals(50, internal.quantity());
        assertEquals(new BigDecimal("200.00"), internal.price());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ExternalTradeRequest buildRequest(String ticker, String buyer, String seller,
                                               int quantity, BigDecimal price) {
        return new ExternalTradeRequest(
                "BROKER-REF-001", ticker, buyer, seller,
                quantity, price, "USD", Instant.now());
    }
}
