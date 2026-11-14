package com.thulirkumver.bookstore.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Immutable checkout snapshot linked to the Razorpay order. */
@Document("payment_intents")
public class PaymentIntent {
    @Id private String razorpayOrderId;
    @Indexed private String userId;
    private long amountPaise;
    private long cashbackPaise;
    /** Promotional wallet funds spent on this checkout, in paise. */
    private long walletRedeemedPaise;
    private long bogoDiscountPaise;
    private long subtotalPaise;
    private long shippingPaise;
    private long discountPaise;
    private String offerCode;
    private String currency = "INR";
    private String couponCode;
    private Address address;
    private List<OrderItem> items = new ArrayList<>();
    private String bookstoreOrderId;
    @Indexed(unique = true, sparse = true) private String razorpayPaymentId;
    private String status = "CREATED";
    private Instant createdAt = Instant.now();

    public String getRazorpayOrderId() { return razorpayOrderId; }
    public void setRazorpayOrderId(String value) { razorpayOrderId = value; }
    public String getUserId() { return userId; }
    public void setUserId(String value) { userId = value; }
    public long getAmountPaise() { return amountPaise; }
    public void setAmountPaise(long value) { amountPaise = value; }
    public long getWalletRedeemedPaise() { return walletRedeemedPaise; }
    public void setWalletRedeemedPaise(long value) { walletRedeemedPaise = value; }
    public long getCashbackPaise() { return cashbackPaise; }
    public void setCashbackPaise(long value) { cashbackPaise = value; }
    public long getBogoDiscountPaise() { return bogoDiscountPaise; }
    public void setBogoDiscountPaise(long value) { bogoDiscountPaise = value; }
    public long getSubtotalPaise() { return subtotalPaise; }
    public void setSubtotalPaise(long v) { subtotalPaise = v; }
    public long getShippingPaise() { return shippingPaise; }
    public void setShippingPaise(long v) { shippingPaise = v; }
    public long getDiscountPaise() { return discountPaise; }
    public void setDiscountPaise(long v) { discountPaise = v; }
    public String getOfferCode() { return offerCode; }
    public void setOfferCode(String v) { offerCode = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String value) { couponCode = value; }
    public Address getAddress() { return address; }
    public void setAddress(Address value) { address = value; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> value) { items = value == null ? new ArrayList<>() : new ArrayList<>(value); }
    public String getBookstoreOrderId() { return bookstoreOrderId; }
    public void setBookstoreOrderId(String value) { bookstoreOrderId = value; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public void setRazorpayPaymentId(String value) { razorpayPaymentId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}
