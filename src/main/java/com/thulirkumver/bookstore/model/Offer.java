package com.thulirkumver.bookstore.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("offers")
public class Offer {
    @Id
    private String id;
    private String title;
    private String code;
    private String description;
    private String discountType;
    private double discountValue;
    private double minOrderAmount;
    private String startDate;
    private String endDate;
    private boolean active;
    private String bannerImage;
    /** COUPON is the default for offers saved before book bundles existed. */
    private String offerType = "COUPON";
    /** Multiple required books: ALL must be bought to unlock a bundle. */
    private java.util.List<OfferBookQuantity> buyItems;
    /** Multiple free book types awarded together. */
    private java.util.List<OfferBookQuantity> giftItems;
    private String buyBookId;
    private String getBookId;
    private int buyQuantity = 1;
    private int getQuantity = 1;

    public Offer() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public double getDiscountValue() { return discountValue; }
    public void setDiscountValue(double discountValue) { this.discountValue = discountValue; }
    public double getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(double minOrderAmount) { this.minOrderAmount = minOrderAmount; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getOfferType() { return offerType == null ? "COUPON" : offerType; }
    public void setOfferType(String value) { offerType = value; }
    public java.util.List<OfferBookQuantity> getBuyItems() {
        return buyItems != null && !buyItems.isEmpty() ? buyItems :
                buyBookId == null || buyBookId.isBlank() ? java.util.List.of() :
                        java.util.List.of(new OfferBookQuantity(buyBookId, buyQuantity));
    }
    public void setBuyItems(java.util.List<OfferBookQuantity> value) { buyItems = value; }
    public java.util.List<OfferBookQuantity> getGiftItems() {
        return giftItems != null && !giftItems.isEmpty() ? giftItems :
                getBookId == null || getBookId.isBlank() ? java.util.List.of() :
                        java.util.List.of(new OfferBookQuantity(getBookId, getQuantity));
    }
    public void setGiftItems(java.util.List<OfferBookQuantity> value) { giftItems = value; }
    public String getBuyBookId() { return buyBookId; }
    public void setBuyBookId(String value) { buyBookId = value; }
    public String getGetBookId() { return getBookId; }
    public void setGetBookId(String value) { getBookId = value; }
    public int getBuyQuantity() { return buyQuantity; }
    public void setBuyQuantity(int value) { buyQuantity = value; }
    public int getGetQuantity() { return getQuantity; }
    public void setGetQuantity(int value) { getQuantity = value; }
    public String getBannerImage() { return bannerImage; }
    public void setBannerImage(String bannerImage) { this.bannerImage = bannerImage; }
}
