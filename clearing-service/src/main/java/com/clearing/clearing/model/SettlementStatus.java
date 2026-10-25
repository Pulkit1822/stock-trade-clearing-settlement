package com.clearing.clearing.model;

public enum SettlementStatus {
    PENDING,
    READY_FOR_NETTING,
    NETTED,
    SETTLED, // Maintained for legacy or RTGS cases if any
    FAILED
}
