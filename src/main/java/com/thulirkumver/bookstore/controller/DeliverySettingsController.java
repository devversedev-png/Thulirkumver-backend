package com.thulirkumver.bookstore.controller;

import com.thulirkumver.bookstore.model.DeliverySettings;
import com.thulirkumver.bookstore.service.DeliverySettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DeliverySettingsController {
    private final DeliverySettingsService service;

    public DeliverySettingsController(DeliverySettingsService service) {
        this.service = service;
    }

    @GetMapping("/delivery-settings")
    public DeliverySettings publicSettings() { return service.current(); }

    @GetMapping("/admin/delivery-settings")
    public DeliverySettings adminSettings() { return service.current(); }

    @PutMapping("/admin/delivery-settings")
    public ResponseEntity<DeliverySettings> update(@RequestBody DeliverySettings next) {
        return ResponseEntity.ok(service.save(next));
    }
}
