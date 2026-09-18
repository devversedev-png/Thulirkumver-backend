package com.thulirkumver.bookstore.controller;

import com.thulirkumver.bookstore.model.HomeSection;
import com.thulirkumver.bookstore.model.HomeSettings;
import com.thulirkumver.bookstore.repository.HomeSectionRepository;
import com.thulirkumver.bookstore.repository.HomeSettingsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HomeContentController {
    private static final List<Map<String, String>> SECTION_DEFINITIONS = List.of(
            Map.of("key", "discounted", "title", "Discounted Books", "description", "Books selected for special discounts."),
            Map.of("key", "top-selling", "title", "Top Selling Books", "description", "Books selected by the admin as top sellers."),
            Map.of("key", "highly-featured", "title", "Highly Featured Books", "description", "Books the admin wants to highlight prominently."),
            Map.of("key", "cashback", "title", "Cashback Books", "description", "Books selected for cashback promotions."),
            Map.of("key", "new-arrivals", "title", "New Arrivals", "description", "Recently added books selected for the home page."),
            Map.of("key", "best-rated", "title", "Best Rated Books", "description", "Books selected for their strong ratings."),
            Map.of("key", "trending", "title", "Trending Books", "description", "Books currently selected as trending."),
            Map.of("key", "recommended", "title", "Recommended Books", "description", "Books personally selected for recommendations.")
    );

    private final HomeSectionRepository sectionRepository;
    private final HomeSettingsRepository settingsRepository;

    public HomeContentController(HomeSectionRepository sectionRepository,
                                 HomeSettingsRepository settingsRepository) {
        this.sectionRepository = sectionRepository;
        this.settingsRepository = settingsRepository;
    }

    @GetMapping("/home-sections")
    public List<HomeSection> publicSections() {
        return sectionRepository.findAllByOrderByDisplayOrderAsc().stream()
                .filter(HomeSection::isActive)
                .filter(section -> section.getBookIds() != null && !section.getBookIds().isEmpty())
                .toList();
    }

    @GetMapping("/home-settings")
    public HomeSettings publicSettings() {
        return settingsRepository.findById("home").orElseGet(HomeSettings::new);
    }

    @GetMapping("/admin/home-sections")
    public List<HomeSection> adminSections() {
        return ensureDefaultSections();
    }

    @PutMapping("/admin/home-sections")
    public List<HomeSection> saveSections(@RequestBody List<HomeSection> sections) {
        List<HomeSection> saved = new ArrayList<>();
        for (HomeSection incoming : sections == null ? List.<HomeSection>of() : sections) {
            if (incoming.getKey() == null || incoming.getKey().isBlank()) continue;
            HomeSection target = sectionRepository.findByKeyIgnoreCase(incoming.getKey())
                    .orElseGet(HomeSection::new);
            target.setKey(incoming.getKey());
            target.setTitle(incoming.getTitle());
            target.setDescription(incoming.getDescription());
            target.setBookIds(incoming.getBookIds());
            target.setActive(incoming.isActive());
            target.setDisplayOrder(incoming.getDisplayOrder());
            saved.add(sectionRepository.save(target));
        }
        return ensureDefaultSections();
    }

    @GetMapping("/admin/home-settings")
    public HomeSettings adminSettings() {
        return settingsRepository.findById("home").orElseGet(HomeSettings::new);
    }

    @PutMapping("/admin/home-settings")
    public ResponseEntity<HomeSettings> saveSettings(@RequestBody HomeSettings settings) {
        if (settings == null) settings = new HomeSettings();
        settings.setId("home");
        List<String> urls = settings.getBannerUrls() == null
                ? List.of()
                : settings.getBannerUrls().stream().limit(4).filter(value -> value != null && !value.isBlank()).toList();
        settings.setBannerUrls(urls);
        int maxIndex = Math.max(0, urls.size() - 1);
        settings.setActiveBannerIndex(Math.min(Math.max(settings.getActiveBannerIndex(), 0), maxIndex));
        return ResponseEntity.ok(settingsRepository.save(settings));
    }

    private List<HomeSection> ensureDefaultSections() {
        List<HomeSection> current = sectionRepository.findAllByOrderByDisplayOrderAsc();
        for (int index = 0; index < SECTION_DEFINITIONS.size(); index++) {
            Map<String, String> definition = SECTION_DEFINITIONS.get(index);
            HomeSection section = sectionRepository.findByKeyIgnoreCase(definition.get("key"))
                    .orElseGet(HomeSection::new);
            if (section.getKey() == null || section.getKey().isBlank()) {
                section.setKey(definition.get("key"));
                section.setTitle(definition.get("title"));
                section.setDescription(definition.get("description"));
                section.setBookIds(List.of());
                section.setActive(true);
                section.setDisplayOrder(index);
                sectionRepository.save(section);
            } else {
                boolean changed = false;
                if (section.getTitle() == null || section.getTitle().isBlank()) {
                    section.setTitle(definition.get("title"));
                    changed = true;
                }
                if (section.getDescription() == null || section.getDescription().isBlank()) {
                    section.setDescription(definition.get("description"));
                    changed = true;
                }
                if (changed) sectionRepository.save(section);
            }
        }
        return sectionRepository.findAllByOrderByDisplayOrderAsc().stream()
                .sorted(Comparator.comparingInt(HomeSection::getDisplayOrder))
                .toList();
    }
}
