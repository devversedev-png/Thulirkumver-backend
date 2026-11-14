package com.thulirkumver.bookstore.controller;

import com.thulirkumver.bookstore.model.User;
import com.thulirkumver.bookstore.service.UserService;
import com.thulirkumver.bookstore.service.WalletService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {
    private final WalletService wallet;
    private final UserService users;

    public WalletController(WalletService wallet, UserService users) {
        this.wallet = wallet;
        this.users = users;
    }

    @GetMapping
    public Map<String, Object> getWallet(Authentication authentication) {
        User user = users.getByEmail(authentication.getName());
        long balance = wallet.balancePaise(user.getId());
        return Map.of("balancePaise", balance, "currency", "INR", "transactions", wallet.history(user.getId()));
    }
}
