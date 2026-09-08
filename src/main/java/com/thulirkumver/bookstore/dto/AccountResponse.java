package com.thulirkumver.bookstore.dto;

import com.thulirkumver.bookstore.model.Address;
import com.thulirkumver.bookstore.model.CartItem;

import java.util.List;

public record AccountResponse(
        String id,
        String name,
        String email,
        String mobile,
        List<String> wishlist,
        List<CartItem> cart,
        List<Address> addresses
) {
}
