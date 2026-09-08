package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.dto.AccountResponse;
import com.thulirkumver.bookstore.model.Book;
import com.thulirkumver.bookstore.model.CartItem;
import com.thulirkumver.bookstore.model.User;
import com.thulirkumver.bookstore.repository.BookRepository;
import com.thulirkumver.bookstore.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final BookRepository bookRepository;

    public UserService(UserRepository userRepository, BookRepository bookRepository) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
    }

    public User getByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public AccountResponse getAccount(String email) {
        return toResponse(getByEmail(email));
    }

    public AccountResponse addWishlist(String email, String bookId) {
        validateBook(bookId);
        User user = getByEmail(email);
        if (!user.getWishlist().contains(bookId)) {
            user.getWishlist().add(bookId);
        }
        return toResponse(userRepository.save(user));
    }

    public AccountResponse removeWishlist(String email, String bookId) {
        User user = getByEmail(email);
        user.getWishlist().remove(bookId);
        return toResponse(userRepository.save(user));
    }

    public synchronized AccountResponse addToCart(String email, String bookId, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be at least 1");
        Book book = getSellableBook(bookId);
        User user = getByEmail(email);
        normalize(user);

        CartItem existing = user.getCart().stream()
                .filter(item -> item.getBookId().equals(bookId))
                .findFirst()
                .orElse(null);

        if (existing == null) {
            reserveStock(book, quantity);
            user.getCart().add(new CartItem(bookId, quantity, true));
        } else if (!existing.isStockReserved()) {
            int totalNeeded = existing.getQuantity() + quantity;
            reserveStock(book, totalNeeded);
            existing.setQuantity(totalNeeded);
            existing.setStockReserved(true);
        } else {
            reserveStock(book, quantity);
            existing.setQuantity(existing.getQuantity() + quantity);
        }

        return toResponse(userRepository.save(user));
    }

    public synchronized AccountResponse updateCart(String email, String bookId, int quantity) {
        User user = getByEmail(email);
        normalize(user);
        CartItem item = user.getCart().stream()
                .filter(cartItem -> cartItem.getBookId().equals(bookId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Book is not in cart"));

        if (quantity <= 0) {
            return removeFromCart(email, bookId);
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found"));

        if (!item.isStockReserved()) {
            if (!book.isAvailable()) throw new IllegalArgumentException(book.getTitle() + " is currently unavailable");
            reserveStock(book, quantity);
            item.setQuantity(quantity);
            item.setStockReserved(true);
        } else {
            int difference = quantity - item.getQuantity();
            if (difference > 0) {
                if (!book.isAvailable()) throw new IllegalArgumentException(book.getTitle() + " is currently unavailable");
                reserveStock(book, difference);
            } else if (difference < 0) {
                releaseStock(book, -difference);
            }
            item.setQuantity(quantity);
        }

        return toResponse(userRepository.save(user));
    }

    public synchronized AccountResponse removeFromCart(String email, String bookId) {
        User user = getByEmail(email);
        normalize(user);
        CartItem item = user.getCart().stream()
                .filter(cartItem -> cartItem.getBookId().equals(bookId))
                .findFirst()
                .orElse(null);

        if (item != null && item.isStockReserved()) {
            bookRepository.findById(bookId).ifPresent(book -> releaseStock(book, item.getQuantity()));
        }
        user.getCart().removeIf(cartItem -> cartItem.getBookId().equals(bookId));
        return toResponse(userRepository.save(user));
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    private Book getSellableBook(String bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found"));
        if (!book.isAvailable()) {
            throw new IllegalArgumentException(book.getTitle() + " is currently out of stock");
        }
        return book;
    }

    private void reserveStock(Book book, int quantity) {
        if (book.getStock() < quantity) {
            throw new IllegalArgumentException(book.getTitle() + " has only " + book.getStock() + " left");
        }
        book.setStock(book.getStock() - quantity);
        bookRepository.save(book);
    }

    private void releaseStock(Book book, int quantity) {
        book.setStock(book.getStock() + quantity);
        bookRepository.save(book);
    }

    private void validateBook(String bookId) {
        if (!bookRepository.existsById(bookId)) {
            throw new IllegalArgumentException("Book not found");
        }
    }

    private void normalize(User user) {
        if (user.getWishlist() == null) user.setWishlist(new ArrayList<>());
        if (user.getCart() == null) user.setCart(new ArrayList<>());
        if (user.getAddresses() == null) user.setAddresses(new ArrayList<>());
    }

    private AccountResponse toResponse(User user) {
        normalize(user);
        return new AccountResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getMobile(),
                user.getWishlist(),
                user.getCart(),
                user.getAddresses()
        );
    }
}
