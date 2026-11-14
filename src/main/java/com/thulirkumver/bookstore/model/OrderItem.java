package com.thulirkumver.bookstore.model;

public class OrderItem {
    private String bookId;
    private String title;
    private String image;
    private double price;
    private int quantity;
    /** Number of this item’s units included free under a BOGO checkout. */
    private int bogoFreeQuantity;

    public OrderItem() {
    }

    public OrderItem(String bookId, String title, String image, double price, int quantity) {
        this.bookId = bookId;
        this.title = title;
        this.image = image;
        this.price = price;
        this.quantity = quantity;
    }

    public String getBookId() { return bookId; }
    public void setBookId(String bookId) { this.bookId = bookId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public int getBogoFreeQuantity() { return bogoFreeQuantity; }
    public void setBogoFreeQuantity(int value) { bogoFreeQuantity = value; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
