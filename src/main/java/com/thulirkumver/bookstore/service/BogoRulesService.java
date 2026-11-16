package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.model.Book;
import com.thulirkumver.bookstore.model.Offer;
import com.thulirkumver.bookstore.model.OrderItem;
import com.thulirkumver.bookstore.repository.BookRepository;
import com.thulirkumver.bookstore.repository.OfferRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-authoritative Buy X Get Y rule engine. A gift is automatically added to the
 * order; customers only put the qualifying paid book into their cart. */
@Service
public class BogoRulesService {
    private final OfferRepository offers;
    private final BookRepository books;

    public BogoRulesService(OfferRepository offers, BookRepository books) {
        this.offers = offers;
        this.books = books;
    }

    public BogoCalculator.Result snapshot(List<OrderItem> purchased) {
        String today = LocalDate.now().toString();
        Map<String, Offer> rulesByBuyBook = new HashMap<>();
        offers.findAll().stream()
                .filter(Offer::isActive)
                .filter(o -> "BUY_GET".equalsIgnoreCase(o.getOfferType()))
                .filter(o -> o.getBuyBookId() != null && o.getGetBookId() != null)
                .filter(o -> o.getBuyQuantity() >= 1 && o.getGetQuantity() >= 1)
                .filter(o -> o.getStartDate() == null || o.getStartDate().isBlank()
                        || o.getStartDate().compareTo(today) <= 0)
                .filter(o -> o.getEndDate() == null || o.getEndDate().isBlank()
                        || o.getEndDate().compareTo(today) >= 0)
                .sorted(Comparator.comparing(Offer::getId, Comparator.nullsLast(String::compareTo)))
                .forEach(o -> rulesByBuyBook.putIfAbsent(o.getBuyBookId(), o));

        Map<String, Integer> requiredGifts = new LinkedHashMap<>();
        for (OrderItem paid : purchased) {
            Offer rule = rulesByBuyBook.get(paid.getBookId());
            if (rule == null) continue;
            int gifts = BogoCalculator.freeUnits(paid.getQuantity(), rule.getBuyQuantity(), rule.getGetQuantity());
            if (gifts > 0) {
                requiredGifts.merge(rule.getGetBookId(), gifts, Math::addExact);
            }
        }
        List<OrderItem> gifts = new ArrayList<>();
        long freeValuePaise = 0;
        for (Map.Entry<String, Integer> entry : requiredGifts.entrySet()) {
            Book giftBook = books.findById(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("The free book in an active promotion is missing"));
            int qty = entry.getValue();
            // Cart stock has already been reserved for paid copies by UserService.
            // Additional gift copies are reserved inside the order transaction.
            if (!giftBook.isAvailable() || giftBook.getStock() < qty)
                throw new IllegalArgumentException("Not enough free-book stock for " + giftBook.getTitle()
                        + ". Please contact the store or try again later.");
            if (!Double.isFinite(giftBook.getPrice()) || giftBook.getPrice() < 0)
                throw new IllegalArgumentException("Invalid price for free book");
            long unitPaise = Math.round(giftBook.getPrice() * 100.0);
            freeValuePaise = Math.addExact(freeValuePaise, Math.multiplyExact(unitPaise, qty));
            OrderItem gift = new OrderItem(giftBook.getId(), giftBook.getTitle(), giftBook.getImage(),
                    giftBook.getPrice(), qty);
            gift.setBogoFreeQuantity(qty);
            gifts.add(gift);
        }
        return new BogoCalculator.Result(freeValuePaise, List.copyOf(purchased), List.copyOf(gifts));
    }
}
