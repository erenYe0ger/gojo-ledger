package com.wallet.gojo.ledger.domain.enums;

public enum AccountType {
    ASSET,      // Cash we hold, or money owed to us
    LIABILITY,  // Customer deposits (money we owe back to users)
    EQUITY,     // Owner's residual stake
    REVENUE,    // Fees earned by our system
    EXPENSE     // Costs incurred by our system
}
