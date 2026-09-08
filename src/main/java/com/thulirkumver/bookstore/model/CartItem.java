package com.thulirkumver.bookstore.model;

public class CartItem {
    private String bookId;
    private int quantity;
    private Boolean stockReserved;

    public CartItem() {
    }

    public CartItem(String bookId, int quantity) {
        this(bookId, quantity, false);
    }

    public CartItem(String bookId, int quantity, boolean stockReserved) {
        this.bookId = bookId;
        this.quantity = quantity;
        this.stockReserved = stockReserved;
    }

    public String getBookId() { return bookId; }
    public void setBookId(String bookId) { this.bookId = bookId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public boolean isStockReserved() { return Boolean.TRUE.equals(stockReserved); }
    public void setStockReserved(Boolean stockReserved) { this.stockReserved = stockReserved; }
}
