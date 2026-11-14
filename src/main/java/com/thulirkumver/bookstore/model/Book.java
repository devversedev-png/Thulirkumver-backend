package com.thulirkumver.bookstore.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("books")
public class Book {
    @Id
    private String id;
    private String title;
    private String author;
    private String category;
    private String language;
    private String publisher;
    private String isbn;
    private String description;
    private String image;
    /** Current customer selling price. Always derived: basePrice, minus any admin promotion. */
    private double price;
    /** Printed MRP. Informational only; never used as the base for promotions. */
    private double originalPrice;
    /** Normal selling price (before any promotion). Null only on records not migrated yet. */
    private Double basePrice;
    /** Active admin promotion: PERCENT or FIXED. Null / zero value means no promotion. */
    private String discountType;
    private Double discountValue;
    private double rating;
    private int reviewCount;
    private int stock;
    private boolean featured;
    private boolean newArrival;
    private Boolean available;

    public Book() {
    }

    public Book(String title, String author, String category, String language, String publisher,
                String isbn, String description, String image, double price, double originalPrice,
                double rating, int reviewCount, int stock, boolean featured, boolean newArrival) {
        this.title = title;
        this.author = author;
        this.category = category;
        this.language = language;
        this.publisher = publisher;
        this.isbn = isbn;
        this.description = description;
        this.image = image;
        this.price = price;
        this.originalPrice = originalPrice;
        this.rating = rating;
        this.reviewCount = reviewCount;
        this.stock = stock;
        this.featured = featured;
        this.newArrival = newArrival;
        this.available = true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public double getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(double originalPrice) { this.originalPrice = originalPrice; }
    public Double getBasePrice() { return basePrice; }
    public void setBasePrice(Double basePrice) { this.basePrice = basePrice; }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public Double getDiscountValue() { return discountValue; }
    public void setDiscountValue(Double discountValue) { this.discountValue = discountValue; }

    // ---- Pricing helpers. Deliberately not bean-style names so Jackson / Spring Data ignore them. ----

    public boolean hasStoredBasePrice() { return basePrice != null && basePrice > 0; }

    /** The normal selling price: the stored base, or (legacy records) the current price. */
    public double normalPrice() { return hasStoredBasePrice() ? basePrice : price; }

    public boolean hasActiveDiscount() {
        return discountValue != null && discountValue > 0 && BookPricing.isValidType(discountType);
    }

    /** Sets the normal selling price and recomputes the selling price from it. Keeps any promotion. */
    public void changeNormalPrice(double normal) {
        this.basePrice = BookPricing.round2(normal);
        reprice();
    }

    /** Applies a promotion on top of the normal selling price (never on an already discounted amount). */
    public void applyDiscount(String type, double value) {
        if (!hasStoredBasePrice()) this.basePrice = BookPricing.round2(price);
        this.discountType = type;
        this.discountValue = value;
        reprice();
    }

    /** Removes the promotion and restores the normal selling price. */
    public void clearDiscount() {
        this.discountType = null;
        this.discountValue = null;
        reprice();
    }

    private void reprice() {
        if (!hasStoredBasePrice()) return;
        if (hasActiveDiscount() && "FIXED".equals(discountType) && discountValue >= basePrice) {
            // A fixed amount can never take a book to zero or below; the promotion no longer applies.
            this.discountType = null;
            this.discountValue = null;
        }
        this.price = hasActiveDiscount()
                ? BookPricing.discounted(basePrice, discountType, discountValue)
                : basePrice;
    }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = Math.max(0, stock); }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public boolean isNewArrival() { return newArrival; }
    public void setNewArrival(boolean newArrival) { this.newArrival = newArrival; }
    public boolean isAvailable() { return available == null || available; }
    public void setAvailable(Boolean available) { this.available = available; }
}
