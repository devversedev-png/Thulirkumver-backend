package com.thulirkumver.bookstore.controller;

import com.thulirkumver.bookstore.model.Book;
import com.thulirkumver.bookstore.model.HomepageSlide;
import com.thulirkumver.bookstore.repository.BookRepository;
import com.thulirkumver.bookstore.repository.HomepageSlideRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api")
public class HomepageSlideController {
    private final HomepageSlideRepository slides;
    private final BookRepository books;

    public HomepageSlideController(HomepageSlideRepository slides, BookRepository books) {
        this.slides = slides;
        this.books = books;
    }

    @GetMapping("/homepage-slides/configured")
    public Map<String, Boolean> configured() { return Map.of("configured", slides.count() > 0); }

    @GetMapping("/homepage-slides")
    public List<Map<String, Object>> published() {
        return slides.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .filter(HomepageSlide::isActive).map(this::toView).toList();
    }

    @GetMapping("/admin/homepage-slides")
    public List<Map<String, Object>> all() {
        return slides.findAllByOrderByDisplayOrderAscIdAsc().stream().map(this::toView).toList();
    }

    @PostMapping("/admin/homepage-slides")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody HomepageSlide input) {
        HomepageSlide slide = validate(input);
        slide.setId(null); // no client-supplied IDs
        slide.setDisplayOrder(slides.findAll().stream().mapToInt(HomepageSlide::getDisplayOrder).max().orElse(-1) + 1);
        return toView(slides.save(slide));
    }

    @PutMapping("/admin/homepage-slides/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody HomepageSlide input) {
        HomepageSlide saved = slides.findById(id).orElseThrow(() -> missing());
        HomepageSlide checked = validate(input);
        saved.setTitle(checked.getTitle());
        saved.setDescription(checked.getDescription());
        saved.setEyebrow(checked.getEyebrow());
        saved.setBookId(checked.getBookId());
        saved.setImageUrl(checked.getImageUrl());
        saved.setTheme(checked.getTheme());
        saved.setButtonText(checked.getButtonText());
        saved.setButtonLink(checked.getButtonLink());
        saved.setActive(checked.isActive());
        return toView(slides.save(saved));
    }

    @DeleteMapping("/admin/homepage-slides/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        HomepageSlide slide = slides.findById(id).orElseThrow(() -> missing());
        slides.delete(slide);
    }

    public record ReorderRequest(List<String> ids) {}

    @PutMapping("/admin/homepage-slides/reorder")
    public List<Map<String, Object>> reorder(@RequestBody ReorderRequest request) {
        List<HomepageSlide> current = slides.findAllByOrderByDisplayOrderAscIdAsc();
        List<String> ids = request == null ? null : request.ids();
        if (ids == null || ids.size() != current.size() || new HashSet<>(ids).size() != ids.size()
                || !new HashSet<>(ids).equals(new HashSet<>(current.stream().map(HomepageSlide::getId).toList()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supply every existing slide ID exactly once");
        }
        Map<String, HomepageSlide> byId = new HashMap<>();
        current.forEach(s -> byId.put(s.getId(), s));
        List<HomepageSlide> reordered = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            HomepageSlide slide = byId.get(ids.get(i));
            slide.setDisplayOrder(i);
            reordered.add(slide);
        }
        slides.saveAll(reordered);
        return slides.findAllByOrderByDisplayOrderAscIdAsc().stream().map(this::toView).toList();
    }

    private HomepageSlide validate(HomepageSlide slide) {
        if (slide == null || clean(slide.getTitle()).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slide heading is required");
        slide.setTitle(limit(slide.getTitle(), 130));
        slide.setDescription(limit(slide.getDescription(), 350));
        slide.setEyebrow(limit(slide.getEyebrow(), 90));
        slide.setButtonText(limit(slide.getButtonText(), 75));
        String theme = clean(slide.getTheme()).toLowerCase(Locale.ROOT);
        if (!Set.of("peach", "cream", "green", "sage", "lavender", "sky", "rose", "mint", "sand", "sunset").contains(theme))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported slide theme");
        slide.setTheme(theme);
        String bookId = clean(slide.getBookId());
        if (!bookId.isEmpty() && !books.existsById(bookId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected book does not exist");
        slide.setBookId(bookId);
        String image = clean(slide.getImageUrl());
        if (!image.isEmpty() && !safeUrl(image))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image URL must be HTTPS, HTTP or an internal path");
        slide.setImageUrl(image);
        String target = clean(slide.getButtonLink());
        if (!target.isEmpty() && !(target.startsWith("/") && !target.startsWith("//") && !target.contains("\\")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Button destination must be a local path beginning with /");
        if (target.contains("BOOK_ID")) {
            target = !bookId.isEmpty() ? "/book/" + bookId : "/shop";
        }
        if (target.isBlank()) {
            target = !bookId.isEmpty() ? "/book/" + bookId : "/shop";
        }
        slide.setButtonLink(target);
        return slide;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String limit(String value, int max) {
        String cleaned = clean(value);
        if (cleaned.length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Text exceeds maximum length of " + max);
        return cleaned;
    }
    private static boolean safeUrl(String url) {
        return (url.startsWith("/") && !url.startsWith("//")) || url.startsWith("https://") || url.startsWith("http://");
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Slide not found"); }

    private Map<String, Object> toView(HomepageSlide slide) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", slide.getId());
        result.put("title", slide.getTitle());
        result.put("description", slide.getDescription());
        result.put("eyebrow", slide.getEyebrow());
        result.put("bookId", slide.getBookId());
        result.put("imageUrl", slide.getImageUrl());
        result.put("theme", slide.getTheme());
        result.put("buttonText", slide.getButtonText());
        String link = clean(slide.getButtonLink());
        String featuredId = clean(slide.getBookId());
        if (link.contains("BOOK_ID")) {
            link = !featuredId.isEmpty() ? "/book/" + featuredId : "/shop";
        }
        if (link.isEmpty()) link = !featuredId.isEmpty() ? "/book/" + featuredId : "/shop";
        result.put("buttonLink", link);
        result.put("active", slide.isActive());
        result.put("displayOrder", slide.getDisplayOrder());
        if (slide.getBookId() != null && !slide.getBookId().isBlank()) {
            Optional<Book> book = books.findById(slide.getBookId());
            book.ifPresent(value -> {
                result.put("bookTitle", value.getTitle());
                result.put("bookImage", value.getImage());
                result.put("bookCategory", value.getCategory());
            });
        }
        return result;
    }
}
