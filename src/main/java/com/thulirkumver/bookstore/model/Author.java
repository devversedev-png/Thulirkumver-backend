package com.thulirkumver.bookstore.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("authors")
public class Author {
    @Id
    private String id;
    private String name;
    private String language;
    private String bio;
    private String image;
    private boolean featured;
    private boolean active = true;
    private int displayOrder;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = Math.max(0, displayOrder); }
}
