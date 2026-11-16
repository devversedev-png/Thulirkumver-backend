package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.model.DeliverySettings;
import com.thulirkumver.bookstore.repository.DeliverySettingsRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class DeliverySettingsService {
    private final DeliverySettingsRepository repository;

    public DeliverySettingsService(DeliverySettingsRepository repository) {
        this.repository = repository;
    }

    /** Old stores have no settings record; default to their existing ₹60/₹499 policy. */
    public DeliverySettings current() {
        return repository.findById("default").orElseGet(DeliverySettings::new);
    }

    public DeliverySettings save(DeliverySettings next) {
        if (next == null) throw new IllegalArgumentException("Delivery settings are required");
        if (next.getFeePaise() < 0 || next.getFeePaise() > 1_000_000)
            throw new IllegalArgumentException("Delivery fee must be between ₹0 and ₹10,000");
        if (next.getFreeDeliveryThresholdPaise() < 0 || next.getFreeDeliveryThresholdPaise() > 100_000_000)
            throw new IllegalArgumentException("Free delivery threshold must be between ₹0 and ₹10,00,000");
        next.setId("default");
        return repository.save(next);
    }

    /** Calculate from merchandise after Buy X Get Y; coupons do not change eligibility. */
    public long feePaise(long merchandisePaise) {
        if (merchandisePaise <= 0) return 0;
        DeliverySettings settings = current();
        if (!settings.isEnabled() || settings.isFreeShippingAll()) return 0;
        if (merchandisePaise >= settings.getFreeDeliveryThresholdPaise()) return 0;
        return settings.getFeePaise();
    }

    public double feeForMerchandise(double merchandiseRupees) {
        long paise = BigDecimal.valueOf(merchandiseRupees).movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        return feePaise(paise) / 100.0;
    }
}
