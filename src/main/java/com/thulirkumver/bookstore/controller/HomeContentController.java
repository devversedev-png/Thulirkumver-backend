package com.thulirkumver.bookstore.controller;

import com.thulirkumver.bookstore.model.Book;
import com.thulirkumver.bookstore.model.HomeSection;
import com.thulirkumver.bookstore.model.HomeSettings;
import com.thulirkumver.bookstore.model.Order;
import com.thulirkumver.bookstore.repository.BookRepository;
import com.thulirkumver.bookstore.repository.HomeSectionRepository;
import com.thulirkumver.bookstore.repository.HomeSettingsRepository;
import com.thulirkumver.bookstore.repository.OrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class HomeContentController {
    private static final List<Map<String, String>> SECTION_DEFINITIONS = List.of(
            Map.of(
                    "key", "discounted",
                    "title", "Discounted Books",
                    "description", "Books currently available below their original price."
            ),
            Map.of(
                    "key", "top-selling",
                    "title", "Top Selling Books",
                    "description", "Books with the highest recent sales."
            ),
            Map.of(
                    "key", "highly-featured",
                    "title", "Highly Featured Books",
                    "description", "Books marked as featured or selected manually by the admin."
            ),
            Map.of(
                    "key", "cashback",
                    "title", "Cashback Books",
                    "description", "Books selected for cashback promotions."
            ),
            Map.of(
                    "key", "new-arrivals",
                    "title", "New Arrivals",
                    "description", "Books marked as new arrivals in the catalogue."
            ),
            Map.of(
                    "key", "best-rated",
                    "title", "Best Rated Books",
                    "description", "Books meeting the selected rating and review thresholds."
            ),
            Map.of(
                    "key", "trending",
                    "title", "Trending Books",
                    "description", "Books trending from recent sales or ratings."
            ),
            Map.of(
                    "key", "recommended",
                    "title", "Recommended Books",
                    "description", "Books selected or filtered for recommendations."
            )
    );

    private final HomeSectionRepository sectionRepository;
    private final HomeSettingsRepository settingsRepository;
    private final BookRepository bookRepository;
    private final OrderRepository orderRepository;

    public HomeContentController(HomeSectionRepository sectionRepository,
                                 HomeSettingsRepository settingsRepository,
                                 BookRepository bookRepository,
                                 OrderRepository orderRepository) {
        this.sectionRepository = sectionRepository;
        this.settingsRepository = settingsRepository;
        this.bookRepository = bookRepository;
        this.orderRepository = orderRepository;
    }

    @GetMapping("/home-sections")
    public List<Map<String, Object>> publicSections() {
        List<Book> books = bookRepository.findAll();
        Map<String, Book> byId = books.stream()
                .filter(book -> book.getId() != null)
                .collect(Collectors.toMap(Book::getId, Function.identity(), (a, b) -> a));
        Map<String, Long> sales = salesForDays(30);

        return sectionRepository.findAllByOrderByDisplayOrderAsc().stream()
                .filter(HomeSection::isActive)
                .map(section -> {
                    List<Book> resolved = resolveBooks(section, books, byId, sales);
                    if (resolved.isEmpty()) return null;

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", section.getId());
                    result.put("key", section.getKey());
                    result.put("title", section.getTitle());
                    result.put("description", section.getDescription());
                    result.put("active", section.isActive());
                    result.put("displayOrder", section.getDisplayOrder());
                    result.put("config", section.getConfig());
                    result.put("bookIds", resolved.stream().map(Book::getId).toList());
                    result.put("books", resolved);
                    return result;
                })
                .filter(Objects::nonNull)
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
            target.setConfig(normalizeConfig(incoming.getKey(), incoming.getConfig()));
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
                : settings.getBannerUrls().stream()
                .limit(4)
                .filter(value -> value != null && !value.isBlank())
                .toList();
        settings.setBannerUrls(urls);
        int maxIndex = Math.max(0, urls.size() - 1);
        settings.setActiveBannerIndex(Math.min(Math.max(settings.getActiveBannerIndex(), 0), maxIndex));
        return ResponseEntity.ok(settingsRepository.save(settings));
    }

    private List<HomeSection> ensureDefaultSections() {
        for (int index = 0; index < SECTION_DEFINITIONS.size(); index++) {
            Map<String, String> definition = SECTION_DEFINITIONS.get(index);
            HomeSection section = sectionRepository.findByKeyIgnoreCase(definition.get("key"))
                    .orElseGet(HomeSection::new);

            boolean changed = false;
            if (section.getKey() == null || section.getKey().isBlank()) {
                section.setKey(definition.get("key"));
                section.setTitle(definition.get("title"));
                section.setDescription(definition.get("description"));
                section.setBookIds(List.of());
                section.setActive(true);
                section.setDisplayOrder(index);
                changed = true;
            } else {
                if (section.getTitle() == null || section.getTitle().isBlank()) {
                    section.setTitle(definition.get("title"));
                    changed = true;
                }
                if (section.getDescription() == null || section.getDescription().isBlank()) {
                    section.setDescription(definition.get("description"));
                    changed = true;
                }
            }

            Map<String, Object> defaults = defaultConfig(section.getKey());
            if (section.getConfig() == null || section.getConfig().isEmpty()) {
                section.setConfig(defaults);
                changed = true;
            }

            if (section.getDisplayOrder() == 0 && index != 0 && section.getId() == null) {
                section.setDisplayOrder(index);
                changed = true;
            }

            if (changed) sectionRepository.save(section);
        }

        return sectionRepository.findAllByOrderByDisplayOrderAsc().stream()
                .sorted(Comparator.comparingInt(HomeSection::getDisplayOrder))
                .toList();
    }

    private Map<String, Object> defaultConfig(String key) {
        Map<String, Object> config = new HashMap<>();
        config.put("limit", 8);

        switch (key) {
            case "discounted" -> {
                config.put("mode", "AUTO");
                config.put("minDiscountPercent", 10);
            }
            case "top-selling" -> {
                config.put("mode", "AUTO");
                config.put("periodDays", 30);
                config.put("basis", "QUANTITY");
            }
            case "highly-featured" -> {
                config.put("mode", "FEATURED_FLAG");
            }
            case "cashback" -> {
                config.put("mode", "MANUAL");
                config.put("cashbackType", "PERCENT");
                config.put("cashbackValue", 5);
                config.put("maxCashback", 0);
            }
            case "new-arrivals" -> {
                config.put("mode", "NEW_ARRIVAL_FLAG");
            }
            case "best-rated" -> {
                config.put("mode", "AUTO");
                config.put("minRating", 4.0);
                config.put("minReviews", 5);
            }
            case "trending" -> {
                config.put("mode", "AUTO");
                config.put("periodDays", 7);
                config.put("basis", "SALES");
            }
            case "recommended" -> {
                config.put("mode", "MANUAL");
                config.put("category", "");
                config.put("author", "");
                config.put("sourceBookId", "");
            }
        }
        return config;
    }

    private Map<String, Object> normalizeConfig(String key, Map<String, Object> incoming) {
        Map<String, Object> config = defaultConfig(key);
        if (incoming != null) config.putAll(incoming);
        config.put("limit", intValue(config.get("limit"), 8, 1, 30));

        switch (key) {
            case "discounted" -> config.put("minDiscountPercent", doubleValue(config.get("minDiscountPercent"), 10, 0, 100));
            case "top-selling", "trending" -> config.put("periodDays", intValue(config.get("periodDays"), key.equals("trending") ? 7 : 30, 1, 365));
            case "best-rated" -> {
                config.put("minRating", doubleValue(config.get("minRating"), 4, 0, 5));
                config.put("minReviews", intValue(config.get("minReviews"), 5, 0, 100000));
            }
            case "cashback" -> {
                config.put("cashbackValue", doubleValue(config.get("cashbackValue"), 5, 0, 100000));
                config.put("maxCashback", doubleValue(config.get("maxCashback"), 0, 0, 1000000));
            }
        }
        return config;
    }

    private List<Book> resolveBooks(HomeSection section,
                                    List<Book> allBooks,
                                    Map<String, Book> byId,
                                    Map<String, Long> defaultSales) {
        String key = section.getKey();
        Map<String, Object> config = section.getConfig() == null ? Map.of() : section.getConfig();
        int limit = intValue(config.get("limit"), 8, 1, 30);
        List<Book> candidates = allBooks.stream().filter(Book::isAvailable).toList();

        return switch (key) {
            case "discounted" -> {
                double minPercent = doubleValue(config.get("minDiscountPercent"), 10, 0, 100);
                List<Book> result = candidates.stream()
                        .filter(book -> book.getOriginalPrice() > book.getPrice() && discountPercent(book) >= minPercent)
                        .sorted(Comparator.comparingDouble(this::discountPercent).reversed())
                        .limit(limit)
                        .toList();
                yield result;
            }
            case "top-selling" -> {
                int days = intValue(config.get("periodDays"), 30, 1, 365);
                String basis = stringValue(config.get("basis"), "QUANTITY");
                if ("REVENUE".equalsIgnoreCase(basis)) {
                    Map<String, Double> revenue = salesRevenueForDays(days);
                    yield candidates.stream()
                            .filter(book -> revenue.getOrDefault(book.getId(), 0.0) > 0)
                            .sorted(Comparator.comparingDouble((Book book) -> revenue.getOrDefault(book.getId(), 0.0)).reversed())
                            .limit(limit)
                            .toList();
                }
                Map<String, Long> sales = salesForDays(days);
                yield candidates.stream()
                        .filter(book -> sales.getOrDefault(book.getId(), 0L) > 0)
                        .sorted(Comparator.comparingLong((Book book) -> sales.getOrDefault(book.getId(), 0L)).reversed())
                        .limit(limit)
                        .toList();
            }
            case "highly-featured" -> {
                String mode = stringValue(config.get("mode"), "FEATURED_FLAG");
                if ("MANUAL".equalsIgnoreCase(mode)) {
                    yield manualBooks(section, byId, limit);
                }
                yield candidates.stream().filter(Book::isFeatured).limit(limit).toList();
            }
            case "cashback" -> manualBooks(section, byId, limit);
            case "new-arrivals" -> {
                String mode = stringValue(config.get("mode"), "NEW_ARRIVAL_FLAG");
                if ("MANUAL".equalsIgnoreCase(mode)) yield manualBooks(section, byId, limit);
                yield candidates.stream().filter(Book::isNewArrival).limit(limit).toList();
            }
            case "best-rated" -> {
                double minRating = doubleValue(config.get("minRating"), 4, 0, 5);
                int minReviews = intValue(config.get("minReviews"), 5, 0, 100000);
                yield candidates.stream()
                        .filter(book -> book.getRating() >= minRating && book.getReviewCount() >= minReviews)
                        .sorted(Comparator.comparingDouble(Book::getRating).reversed()
                                .thenComparing(Comparator.comparingInt(Book::getReviewCount).reversed()))
                        .limit(limit)
                        .toList();
            }
            case "trending" -> {
                int days = intValue(config.get("periodDays"), 7, 1, 365);
                String basis = stringValue(config.get("basis"), "SALES");
                if ("RATING".equalsIgnoreCase(basis)) {
                    yield candidates.stream()
                            .sorted(Comparator.comparingDouble(Book::getRating).reversed()
                                    .thenComparing(Comparator.comparingInt(Book::getReviewCount).reversed()))
                            .limit(limit)
                            .toList();
                }
                Map<String, Long> sales = salesForDays(days);
                yield candidates.stream()
                        .filter(book -> sales.getOrDefault(book.getId(), 0L) > 0)
                        .sorted(Comparator.comparingLong((Book book) -> sales.getOrDefault(book.getId(), 0L)).reversed())
                        .limit(limit)
                        .toList();
            }
            case "recommended" -> resolveRecommended(section, candidates, byId, limit);
            default -> manualBooks(section, byId, limit);
        };
    }

    private List<Book> resolveRecommended(HomeSection section,
                                          List<Book> candidates,
                                          Map<String, Book> byId,
                                          int limit) {
        Map<String, Object> config = section.getConfig() == null ? Map.of() : section.getConfig();
        String mode = stringValue(config.get("mode"), "MANUAL");

        if ("CATEGORY".equalsIgnoreCase(mode)) {
            String category = stringValue(config.get("category"), "").trim();
            if (category.isEmpty()) return List.of();
            return candidates.stream()
                    .filter(book -> category.equalsIgnoreCase(book.getCategory()))
                    .limit(limit)
                    .toList();
        }

        if ("AUTHOR".equalsIgnoreCase(mode)) {
            String author = stringValue(config.get("author"), "").trim();
            if (author.isEmpty()) return List.of();
            return candidates.stream()
                    .filter(book -> author.equalsIgnoreCase(book.getAuthor()))
                    .limit(limit)
                    .toList();
        }

        if ("SIMILAR".equalsIgnoreCase(mode)) {
            Book source = byId.get(stringValue(config.get("sourceBookId"), ""));
            if (source == null) return List.of();
            return candidates.stream()
                    .filter(book -> !book.getId().equals(source.getId()))
                    .filter(book -> same(source.getCategory(), book.getCategory()) || same(source.getAuthor(), book.getAuthor()))
                    .limit(limit)
                    .toList();
        }

        return manualBooks(section, byId, limit);
    }

    private List<Book> manualBooks(HomeSection section, Map<String, Book> byId, int limit) {
        if (section.getBookIds() == null) return List.of();
        return section.getBookIds().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(Book::isAvailable)
                .limit(limit)
                .toList();
    }

    private Map<String, Double> salesRevenueForDays(int days) {
        Instant cutoff = Instant.now().minus(Duration.ofDays(Math.max(1, days)));
        Map<String, Double> revenue = new HashMap<>();

        for (Order order : orderRepository.findAll()) {
            if ("CANCELLED".equalsIgnoreCase(order.getStatus())) continue;
            if (order.getCreatedAt() != null && order.getCreatedAt().isBefore(cutoff)) continue;
            if (order.getItems() == null) continue;

            order.getItems().forEach(item -> {
                if (item.getBookId() != null) {
                    revenue.merge(item.getBookId(), item.getPrice() * Math.max(0, item.getQuantity()), Double::sum);
                }
            });
        }
        return revenue;
    }

    private Map<String, Long> salesForDays(int days) {
        Instant cutoff = Instant.now().minus(Duration.ofDays(Math.max(1, days)));
        Map<String, Long> sales = new HashMap<>();

        for (Order order : orderRepository.findAll()) {
            if ("CANCELLED".equalsIgnoreCase(order.getStatus())) continue;
            if (order.getCreatedAt() != null && order.getCreatedAt().isBefore(cutoff)) continue;
            if (order.getItems() == null) continue;

            order.getItems().forEach(item -> {
                if (item.getBookId() != null) {
                    sales.merge(item.getBookId(), (long) Math.max(0, item.getQuantity()), Long::sum);
                }
            });
        }
        return sales;
    }

    private double discountPercent(Book book) {
        if (book.getOriginalPrice() <= 0 || book.getPrice() >= book.getOriginalPrice()) return 0;
        return ((book.getOriginalPrice() - book.getPrice()) / book.getOriginalPrice()) * 100.0;
    }

    private int intValue(Object value, int fallback, int min, int max) {
        try {
            int result = value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
            return Math.min(max, Math.max(min, result));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private double doubleValue(Object value, double fallback, double min, double max) {
        try {
            double result = value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
            return Math.min(max, Math.max(min, result));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private boolean same(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }
}
