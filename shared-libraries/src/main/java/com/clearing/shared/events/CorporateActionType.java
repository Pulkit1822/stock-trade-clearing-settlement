package com.clearing.shared.events;

/**
 * Type of corporate action affecting securities in the clearing system.
 *
 * <p>DIVIDEND: Cash payment distributed to account holders per share owned.
 * STOCK_SPLIT: Number of shares is multiplied; position quantities are adjusted accordingly.
 */
public enum CorporateActionType {

    DIVIDEND,
    STOCK_SPLIT
}
