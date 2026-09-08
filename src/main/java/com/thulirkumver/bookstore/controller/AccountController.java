package com.thulirkumver.bookstore.controller;

import com.thulirkumver.bookstore.dto.AccountResponse;
import com.thulirkumver.bookstore.dto.CartRequest;
import com.thulirkumver.bookstore.dto.QuantityRequest;
import com.thulirkumver.bookstore.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final UserService userService;

    public AccountController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public AccountResponse getAccount(Authentication authentication) {
        return userService.getAccount(authentication.getName());
    }

    @PostMapping("/wishlist/{bookId}")
    public AccountResponse addWishlist(Authentication auth, @PathVariable String bookId) {
        return userService.addWishlist(auth.getName(), bookId);
    }

    @DeleteMapping("/wishlist/{bookId}")
    public AccountResponse removeWishlist(Authentication auth, @PathVariable String bookId) {
        return userService.removeWishlist(auth.getName(), bookId);
    }

    @PostMapping("/cart")
    public AccountResponse addCart(Authentication auth, @Valid @RequestBody CartRequest request) {
        return userService.addToCart(auth.getName(), request.bookId(), request.quantity());
    }

    @PutMapping("/cart/{bookId}")
    public AccountResponse updateCart(Authentication auth, @PathVariable String bookId,
                                      @Valid @RequestBody QuantityRequest request) {
        return userService.updateCart(auth.getName(), bookId, request.quantity());
    }

    @DeleteMapping("/cart/{bookId}")
    public AccountResponse removeCart(Authentication auth, @PathVariable String bookId) {
        return userService.removeFromCart(auth.getName(), bookId);
    }
}
