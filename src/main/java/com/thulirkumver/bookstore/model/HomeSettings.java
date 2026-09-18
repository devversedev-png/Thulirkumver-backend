package com.thulirkumver.bookstore.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document("home_settings")
public class HomeSettings {
    @Id
    private String id = "home";
    private List<String> bannerUrls = new ArrayList<>();
    private int activeBannerIndex = 0;

    public HomeSettings() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<String> getBannerUrls() { return bannerUrls; }
    public void setBannerUrls(List<String> bannerUrls) {
        this.bannerUrls = bannerUrls == null ? new ArrayList<>() : new ArrayList<>(bannerUrls);
    }
    public int getActiveBannerIndex() { return activeBannerIndex; }
    public void setActiveBannerIndex(int activeBannerIndex) { this.activeBannerIndex = activeBannerIndex; }
}
