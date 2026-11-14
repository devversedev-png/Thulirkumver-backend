package com.thulirkumver.bookstore.config;

import com.thulirkumver.bookstore.service.BookPricingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Runs at startup. Safe to run on every start: every step is idempotent and only touches books that
 * carry (or are configured to carry) a promotion. It never blocks the application from starting.
 */
@Component
public class PricingMigration implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(PricingMigration.class);

    private final BookPricingService pricingService;
    private final List<String> restoreToMrp;

    public PricingMigration(BookPricingService pricingService,
                            @Value("${app.pricing-restore-to-mrp:}") String restoreToMrp) {
        this.pricingService = pricingService;
        this.restoreToMrp = Arrays.stream(restoreToMrp.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Override
    public void run(String... args) {
        try {
            pricingService.normalizeLegacyBooks();
            pricingService.syncFromStoredSection();
            pricingService.restoreToMrp(restoreToMrp);
            pricingService.logUnexplainedDiscounts();
        } catch (Exception ex) {
            log.error("Pricing migration failed; catalogue prices were left as they are.", ex);
        }
    }
}
