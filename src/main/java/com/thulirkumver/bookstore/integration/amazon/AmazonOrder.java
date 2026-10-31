package com.thulirkumver.bookstore.integration.amazon;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One order pulled from the Amazon seller account (Orders API v2026-01-01). */
@Document("amazon_orders")
public class AmazonOrder {

    public static class Item {
        public String orderItemId;
        public String asin;
        public String sku;
        public String title;
        public int quantity;
        public double lineTotal;
    }

    @Id
    private String id;
    @Indexed(unique = true)
    private String amazonOrderId;
    private String marketplaceId;
    private String marketplaceName;
    @Indexed
    private Instant createdTime;
    private Instant lastUpdatedTime;
    private String fulfillmentStatus;
    private String fulfilledBy;
    private String buyerName;
    private String buyerEmail;
    private String recipientName;
    private String phone;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String countryCode;
    private double itemsTotal;
    private String currency;
    private List<Item> items = new ArrayList<>();
    private Instant syncedAt;
    /** Only filled when amazon.store-raw=true, to help debug field mapping. */
    private String rawJson;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAmazonOrderId() { return amazonOrderId; }
    public void setAmazonOrderId(String amazonOrderId) { this.amazonOrderId = amazonOrderId; }
    public String getMarketplaceId() { return marketplaceId; }
    public void setMarketplaceId(String marketplaceId) { this.marketplaceId = marketplaceId; }
    public String getMarketplaceName() { return marketplaceName; }
    public void setMarketplaceName(String marketplaceName) { this.marketplaceName = marketplaceName; }
    public Instant getCreatedTime() { return createdTime; }
    public void setCreatedTime(Instant createdTime) { this.createdTime = createdTime; }
    public Instant getLastUpdatedTime() { return lastUpdatedTime; }
    public void setLastUpdatedTime(Instant lastUpdatedTime) { this.lastUpdatedTime = lastUpdatedTime; }
    public String getFulfillmentStatus() { return fulfillmentStatus; }
    public void setFulfillmentStatus(String fulfillmentStatus) { this.fulfillmentStatus = fulfillmentStatus; }
    public String getFulfilledBy() { return fulfilledBy; }
    public void setFulfilledBy(String fulfilledBy) { this.fulfilledBy = fulfilledBy; }
    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }
    public String getBuyerEmail() { return buyerEmail; }
    public void setBuyerEmail(String buyerEmail) { this.buyerEmail = buyerEmail; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public double getItemsTotal() { return itemsTotal; }
    public void setItemsTotal(double itemsTotal) { this.itemsTotal = itemsTotal; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
    public Instant getSyncedAt() { return syncedAt; }
    public void setSyncedAt(Instant syncedAt) { this.syncedAt = syncedAt; }
    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }
}
