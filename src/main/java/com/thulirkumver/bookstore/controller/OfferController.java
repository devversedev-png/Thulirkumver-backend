package com.thulirkumver.bookstore.controller;

import com.thulirkumver.bookstore.model.Offer;
import com.thulirkumver.bookstore.repository.OfferRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/offers")
public class OfferController {
    private final OfferRepository offerRepository;

    public OfferController(OfferRepository offerRepository) {
        this.offerRepository = offerRepository;
    }

    @GetMapping("/active")
    public List<Offer> activeOffers() {
        String today = LocalDate.now().toString();
        return offerRepository.findAll().stream()
                .filter(Offer::isActive)
                .filter(offer -> offer.getStartDate() == null || offer.getStartDate().isBlank() || offer.getStartDate().compareTo(today) <= 0)
                .filter(offer -> offer.getEndDate() == null || offer.getEndDate().isBlank() || offer.getEndDate().compareTo(today) >= 0)
                .toList();
    }
}
