package com.thulirkumver.bookstore.controller;

import com.thulirkumver.bookstore.dto.CheckoutRequest;
import com.thulirkumver.bookstore.model.Order;
import com.thulirkumver.bookstore.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public Order placeOrder(Authentication authentication, @Valid @RequestBody CheckoutRequest request) {
        return orderService.placeOrder(authentication.getName(), request);
    }

    @GetMapping
    public List<Order> getOrders(Authentication authentication) {
        return orderService.getOrders(authentication.getName());
    }

    @GetMapping("/{id}")
    public Order getOrder(Authentication authentication, @PathVariable String id) {
        return orderService.getOrder(authentication.getName(), id);
    }
}
