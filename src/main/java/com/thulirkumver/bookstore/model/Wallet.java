package com.thulirkumver.bookstore.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

/** All amounts are stored in paise to avoid floating point errors. */
@Document("wallets")
public class Wallet {
    @Id private String id; // Same immutable ID as the user
    @Indexed(unique = true) private String userId;
    private long balancePaise;
    private Instant updatedAt;

    public Wallet() {}
    public Wallet(String userId) {
        this.id = userId;
        this.userId = userId;
        this.balancePaise = 0;
        this.updatedAt = Instant.now();
    }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public long getBalancePaise() { return balancePaise; }
    public void setBalancePaise(long balancePaise) { this.balancePaise = balancePaise; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
