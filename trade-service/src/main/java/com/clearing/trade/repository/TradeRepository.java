package com.clearing.trade.repository;

import com.clearing.trade.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Standard JPA Repository for persisting and retrieving Trades.
 */
@Repository
public interface TradeRepository extends JpaRepository<Trade, UUID> {
    // Add custom finder methods later if required (e.g. findByBuyerAccount)
}
