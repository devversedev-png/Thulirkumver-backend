package com.thulirkumver.bookstore.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "store_delivery_settings")
public class DeliverySettings {
    @Id
    private String id = "default";
    private boolean enabled = true;
    private long feePaise = 6000;
    private long freeDeliveryThresholdPaise = 49900;
    private boolean freeShippingAll = false;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getFeePaise() { return feePaise; }
    public void setFeePaise(long feePaise) { this.feePaise = feePaise; }
    public long getFreeDeliveryThresholdPaise() { return freeDeliveryThresholdPaise; }
    public void setFreeDeliveryThresholdPaise(long value) { this.freeDeliveryThresholdPaise = value; }
    public boolean isFreeShippingAll() { return freeShippingAll; }
    public void setFreeShippingAll(boolean freeShippingAll) { this.freeShippingAll = freeShippingAll; }
}
