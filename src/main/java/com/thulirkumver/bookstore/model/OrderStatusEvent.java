package com.thulirkumver.bookstore.model;

import java.time.Instant;

public class OrderStatusEvent {
    private String status;
    private String note;
    private Instant at;

    public OrderStatusEvent() {
    }

    public OrderStatusEvent(String status, String note, Instant at) {
        this.status = status;
        this.note = note;
        this.at = at;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }
}
