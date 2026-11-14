package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.model.Book;
import com.thulirkumver.bookstore.model.BookPricing;
import com.thulirkumver.bookstore.model.BookPricing.Promo;
import com.thulirkumver.bookstore.repository.BookRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Single owner of book selling prices.
 *
 * Model: {@code basePrice} is the normal selling price. A promotion (discountType / discountValue) is
 * always calculated from it, and {@code price} is the resulting customer price. Books that carry no
 * promotion are never touched by {@link #syncPromotions}.
 */
@Service
public class BookPricingService {
    private static final Logger log = LoggerFactory.getLogger(BookPricingService.class);
    static final String BACKUP_COLLECTION = "book_price_backups";
    private static final String RESTORE_REASON = "restore-to-mrp";

    private final BookRepository bookRepository;
    private final MongoTemplate mongoTemplate;

    public BookPricingService(BookRepository bookRepository, MongoTemplate mongoTemplate) {
        this.bookRepository = bookRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /** State of the Discounted Books section as currently stored. */
    private record SectionState(boolean active, boolean manual, Map<String, Promo> configured) {
        /** Promotions that should actually be live: only an active section in MANUAL mode applies any. */
        Map<String, Promo> desired() {
            return active && manual ? configured : Map.of();
        }
    }

    private SectionState readDiscountedSection() {
        Document doc = mongoTemplate.getCollection("home_sections").find(new Document("key", "discounted")).first();
        if (doc == null) return new SectionState(false, false, Map.of());

        Object activeRaw = doc.get("active");
        boolean active = activeRaw == null
                || (activeRaw instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(activeRaw)));
        Map<?, ?> config = doc.get("config") instanceof Map<?, ?> m ? m : Map.of();
        boolean manual = "MANUAL".equalsIgnoreCase(String.valueOf(config.get("mode")));

        Set<String> ids = new HashSet<>();
        if (doc.get("bookIds") instanceof List<?> list) {
            for (Object id : list) if (id != null) ids.add(String.valueOf(id));
        }
        return new SectionState(active, manual, configuredPromotions(config.get("perBookDiscounts"), ids));
    }

    /** Valid promotions (value above zero) for the selected books. Empty or zero means no promotion. */
    private Map<String, Promo> configuredPromotions(Object rawDiscounts, Set<String> selectedIds) {
        Map<String, Promo> result = new LinkedHashMap<>();
        if (!(rawDiscounts instanceof Map<?, ?> discounts)) return result;
        for (String id : selectedIds) {
            if (!(discounts.get(id) instanceof Map<?, ?> entry)) continue;
            Object rawType = entry.get("type");
            String type = rawType == null ? "PERCENT" : String.valueOf(rawType).trim().toUpperCase(Locale.ROOT);
            double value;
            try {
                Object rawValue = entry.get("value");
                value = rawValue instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(rawValue).trim());
            } catch (Exception ex) {
                continue;
            }
            if (!BookPricing.isValidType(type) || !Double.isFinite(value) || value <= 0) continue;
            if ("PERCENT".equals(type) && value > 100) continue;
            result.put(id, new Promo(type, value));
        }
        return result;
    }

    /**
     * Gives every book that predates {@code basePrice} a normal selling price, and rebuilds the ones the
     * old logic had overwritten with a discounted amount. Idempotent: once a book has a base it is skipped.
     */
    public synchronized int normalizeLegacyBooks() {
        List<Book> legacy = bookRepository.findAll().stream()
                .filter(book -> !book.hasStoredBasePrice() && book.getPrice() > 0)
                .toList();
        if (legacy.isEmpty()) return 0;

        SectionState section = readDiscountedSection();
        Map<String, Promo> desired = section.desired();
        int changed = 0;
        for (Book book : legacy) {
            double before = book.getPrice();
            double base = BookPricing.legacyBase(before, book.getOriginalPrice(), section.configured().get(book.getId()));
            book.setBasePrice(BookPricing.round2(base));
            Promo want = desired.get(book.getId());
            if (want != null) book.applyDiscount(want.type(), want.value());
            else book.clearDiscount();
            if (!BookPricing.close(before, book.getPrice())) {
                backup(book, before, "legacy-price-rebuilt");
                log.warn("Pricing migration: '{}' price {} -> {} (normal price {}).",
                        book.getTitle(), before, book.getPrice(), book.getBasePrice());
            }
            bookRepository.save(book);
            changed++;
        }
        log.info("Pricing migration: {} book(s) given a normal selling price.", changed);
        return changed;
    }

    /**
     * Makes live prices match the given promotions. Books in {@code desired} are priced from their normal
     * price; books that carry a promotion but are no longer in {@code desired} are restored to it.
     * Every other book is left exactly as it is.
     */
    public synchronized int syncPromotions(Map<String, Promo> desired) {
        int changed = 0;
        for (Book book : bookRepository.findAll()) {
            Promo want = desired.get(book.getId());
            double beforePrice = book.getPrice();
            Double beforeBase = book.getBasePrice();
            String beforeType = book.getDiscountType();
            Double beforeValue = book.getDiscountValue();

            if (want != null) {
                if (!book.hasStoredBasePrice()) {
                    if (book.getPrice() <= 0) continue;
                    book.setBasePrice(BookPricing.round2(book.getPrice()));
                }
                book.applyDiscount(want.type(), want.value());
            } else if (book.getDiscountType() != null || book.getDiscountValue() != null) {
                book.clearDiscount();
            } else {
                continue;
            }

            boolean same = BookPricing.close(beforePrice, book.getPrice())
                    && Objects.equals(beforeBase, book.getBasePrice())
                    && Objects.equals(beforeType, book.getDiscountType())
                    && Objects.equals(beforeValue, book.getDiscountValue());
            if (same) continue;
            bookRepository.save(book);
            changed++;
        }
        return changed;
    }

    /** Re-applies whatever the stored Discounted Books section currently says. */
    public synchronized int syncFromStoredSection() {
        return syncPromotions(readDiscountedSection().desired());
    }

    /**
     * Opt-in repair (PRICING_RESTORE_TO_MRP): for the listed books (id or exact title) that have no active
     * promotion, set the normal selling price back to the printed MRP. Applied once per book.
     */
    public synchronized int restoreToMrp(List<String> selectors) {
        Set<String> wanted = new HashSet<>();
        for (String selector : selectors) {
            if (selector != null && !selector.isBlank()) wanted.add(selector.trim().toLowerCase(Locale.ROOT));
        }
        if (wanted.isEmpty()) return 0;

        int changed = 0;
        for (Book book : bookRepository.findAll()) {
            boolean match = wanted.contains(String.valueOf(book.getId()).toLowerCase(Locale.ROOT))
                    || (book.getTitle() != null && wanted.contains(book.getTitle().trim().toLowerCase(Locale.ROOT)));
            if (!match) continue;
            if (book.hasActiveDiscount()) {
                log.warn("Restore to MRP skipped for '{}': it has an active promotion. Remove it in Home Sections first.", book.getTitle());
                continue;
            }
            long done = mongoTemplate.getCollection(BACKUP_COLLECTION)
                    .countDocuments(new Document("bookId", book.getId()).append("reason", RESTORE_REASON));
            if (done > 0) continue;
            if (book.getOriginalPrice() <= book.normalPrice()) {
                log.info("Restore to MRP: '{}' already at or above its MRP ({} vs {}); nothing to do.",
                        book.getTitle(), book.normalPrice(), book.getOriginalPrice());
                continue;
            }
            double before = book.getPrice();
            backup(book, before, RESTORE_REASON);
            book.changeNormalPrice(book.getOriginalPrice());
            bookRepository.save(book);
            log.warn("Restore to MRP: '{}' price {} -> {}.", book.getTitle(), before, book.getPrice());
            changed++;
        }
        return changed;
    }

    /**
     * Lists books priced below their MRP with no admin promotion. These are either legitimate (e.g. MRP 499,
     * selling 399) or a leftover discount; the code cannot tell which, so it only reports them.
     */
    public void logUnexplainedDiscounts() {
        for (Book book : bookRepository.findAll()) {
            if (book.hasActiveDiscount() || book.getOriginalPrice() <= book.normalPrice()) continue;
            log.warn("Pricing check: '{}' (id {}) sells at {} while its MRP is {} and no admin promotion is set. "
                            + "Left unchanged. If {} is a leftover discount, edit the selling price in Admin > Inventory "
                            + "or set PRICING_RESTORE_TO_MRP to this book's title.",
                    book.getTitle(), book.getId(), book.normalPrice(), book.getOriginalPrice(), book.normalPrice());
        }
    }

    private void backup(Book book, double oldPrice, String reason) {
        mongoTemplate.getCollection(BACKUP_COLLECTION).insertOne(new Document()
                .append("bookId", book.getId())
                .append("title", book.getTitle())
                .append("oldPrice", oldPrice)
                .append("originalPrice", book.getOriginalPrice())
                .append("newPrice", book.getPrice())
                .append("reason", reason)
                .append("at", new Date()));
    }
}
