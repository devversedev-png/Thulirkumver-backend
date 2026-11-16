package com.thulirkumver.bookstore.model;

/** A required paid title or a free gift, with quantity per complete bundle. */
public class OfferBookQuantity {
    private String bookId;
    private int quantity;
    public OfferBookQuantity() {}
    public OfferBookQuantity(String bookId, int quantity) { this.bookId = bookId; this.quantity = quantity; }
    public String getBookId() { return bookId; }
    public void setBookId(String bookId) { this.bookId = bookId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
