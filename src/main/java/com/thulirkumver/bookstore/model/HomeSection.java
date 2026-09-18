package com.thulirkumver.bookstore.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document("home_sections")
public class HomeSection {
    @Id
    private String id;
    private String key;
    private String title;
    private String description;
    private List<String> bookIds = new ArrayList<>();
    private boolean active = true;
    private int displayOrder;

    public HomeSection() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getBookIds() { return bookIds; }
    public void setBookIds(List<String> bookIds) {
        this.bookIds = bookIds == null ? new ArrayList<>() : new ArrayList<>(bookIds);
    }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}
