package com.thulirkumver.bookstore.integration.amazon;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Single document remembering how far the order sync has got. */
@Document("amazon_sync_state")
public class AmazonSyncState {

    public static final String ORDERS = "orders";

    @Id
    private String id = ORDERS;
    private Instant lastSuccessfulSync;
    /** Orders changed after this moment are fetched on the next run. */
    private Instant syncedUntil;
    private Instant lastAttempt;
    private String lastError;
    private int lastSyncedCount;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Instant getLastSuccessfulSync() { return lastSuccessfulSync; }
    public void setLastSuccessfulSync(Instant v) { this.lastSuccessfulSync = v; }
    public Instant getSyncedUntil() { return syncedUntil; }
    public void setSyncedUntil(Instant v) { this.syncedUntil = v; }
    public Instant getLastAttempt() { return lastAttempt; }
    public void setLastAttempt(Instant v) { this.lastAttempt = v; }
    public String getLastError() { return lastError; }
    public void setLastError(String v) { this.lastError = v; }
    public int getLastSyncedCount() { return lastSyncedCount; }
    public void setLastSyncedCount(int v) { this.lastSyncedCount = v; }
}
