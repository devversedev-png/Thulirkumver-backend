package com.thulirkumver.bookstore.config;

/**
 * Compatibility placeholder. The single transaction manager is defined by
 * WalletMongoTransactionConfig; declaring two managers makes @Transactional ambiguous.
 */
@Deprecated
public final class WalletTransactionConfig {
    private WalletTransactionConfig() {}
}
