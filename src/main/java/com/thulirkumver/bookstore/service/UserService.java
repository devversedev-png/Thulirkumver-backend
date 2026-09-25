package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.dto.AccountResponse;
import com.thulirkumver.bookstore.model.Address;
import com.thulirkumver.bookstore.model.Book;
import com.thulirkumver.bookstore.model.CartItem;
import com.thulirkumver.bookstore.model.User;
import com.thulirkumver.bookstore.repository.BookRepository;
import com.thulirkumver.bookstore.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    public User getById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    /**
     * New JWTs contain the immutable Mongo user id. Older JWTs contained the
     * email, so keep an email fallback during the migration.
     */
    public User getByTokenSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Invalid token subject");
        }

        return userRepository.findById(subject)
                .orElseGet(() -> getByEmail(subject));
    }

    public AccountResponse getAccount(String email) {
        User user = getByEmail(email);
        if (normalize(user)) user = userRepository.save(user);
        return toResponse(user);
    }

    public AccountResponse addWishlist(String email, String bookId) {
        validateBook(bookId);
        User user = getByEmail(email);
        normalize(user);
        if (!user.getWishlist().contains(bookId)) user.getWishlist().add(bookId);
        return toResponse(userRepository.save(user));
    }

    public AccountResponse removeWishlist(String email, String bookId) {
        User user = getByEmail(email);
        normalize(user);
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

        if (quantity <= 0) return removeFromCart(email, bookId);

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

    public AccountResponse addAddress(String email, Address request) {
        User user = getByEmail(email);
        normalize(user);
        Address address = cleanedAddress(request);
        address.setId(UUID.randomUUID().toString());
        if (user.getAddresses().isEmpty()) address.setDefaultAddress(true);
        if (address.isDefaultAddress()) clearDefault(user.getAddresses());
        user.getAddresses().add(address);
        ensureOneDefault(user.getAddresses());
        return toResponse(userRepository.save(user));
    }

    public AccountResponse updateAddress(String email, String addressId, Address request) {
        User user = getByEmail(email);
        normalize(user);
        Address existing = findAddress(user, addressId);
        boolean wasDefault = existing.isDefaultAddress();
        Address updated = cleanedAddress(request);
        updated.setId(existing.getId());
        if (updated.isDefaultAddress()) clearDefault(user.getAddresses());
        else if (wasDefault) updated.setDefaultAddress(true);

        int index = user.getAddresses().indexOf(existing);
        user.getAddresses().set(index, updated);
        ensureOneDefault(user.getAddresses());
        return toResponse(userRepository.save(user));
    }

    public AccountResponse deleteAddress(String email, String addressId) {
        User user = getByEmail(email);
        normalize(user);
        Address existing = findAddress(user, addressId);
        user.getAddresses().remove(existing);
        ensureOneDefault(user.getAddresses());
        return toResponse(userRepository.save(user));
    }

    public AccountResponse makeDefaultAddress(String email, String addressId) {
        User user = getByEmail(email);
        normalize(user);
        Address existing = findAddress(user, addressId);
        clearDefault(user.getAddresses());
        existing.setDefaultAddress(true);
        return toResponse(userRepository.save(user));
    }

    public Address rememberCheckoutAddress(User user, Address request) {
        normalize(user);
        if (request == null) return null;

        if (request.getId() != null && !request.getId().isBlank()) {
            for (Address saved : user.getAddresses()) {
                if (request.getId().equals(saved.getId())) return cleanedAddress(request);
            }
        }

        Address address = cleanedAddress(request);
        address.setId(UUID.randomUUID().toString());
        if (user.getAddresses().isEmpty()) address.setDefaultAddress(true);
        user.getAddresses().add(address);
        ensureOneDefault(user.getAddresses());
        return address;
    }

    public User save(User user) {
        normalize(user);
        return userRepository.save(user);
    }

    private Book getSellableBook(String bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found"));
        if (!book.isAvailable()) throw new IllegalArgumentException(book.getTitle() + " is currently out of stock");
        return book;
    }

    private void reserveStock(Book book, int quantity) {
        if (book.getStock() < quantity) throw new IllegalArgumentException(book.getTitle() + " has only " + book.getStock() + " left");
        book.setStock(book.getStock() - quantity);
        bookRepository.save(book);
    }

    private void releaseStock(Book book, int quantity) {
        book.setStock(book.getStock() + quantity);
        bookRepository.save(book);
    }

    private void validateBook(String bookId) {
        if (!bookRepository.existsById(bookId)) throw new IllegalArgumentException("Book not found");
    }

    private Address findAddress(User user, String addressId) {
        return user.getAddresses().stream()
                .filter(address -> addressId.equals(address.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Address not found"));
    }

    private Address cleanedAddress(Address source) {
        Address target = new Address();
        target.setLabel(cleanText(source.getLabel(), "Home"));
        target.setFullName(cleanText(source.getFullName(), ""));
        target.setMobile(cleanText(source.getMobile(), ""));
        target.setLine1(cleanText(source.getLine1(), ""));
        target.setLine2(cleanText(source.getLine2(), ""));
        target.setCity(cleanText(source.getCity(), ""));
        target.setDistrict(cleanText(source.getDistrict(), ""));
        target.setState(cleanText(source.getState(), ""));
        target.setPincode(cleanText(source.getPincode(), ""));
        target.setLatitude(source.getLatitude());
        target.setLongitude(source.getLongitude());
        target.setLocationLabel(cleanText(source.getLocationLabel(), ""));
        target.setDefaultAddress(source.isDefaultAddress());
        return target;
    }

    private String cleanText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void clearDefault(List<Address> addresses) {
        addresses.forEach(address -> address.setDefaultAddress(false));
    }

    private void ensureOneDefault(List<Address> addresses) {
        if (addresses.isEmpty()) return;
        if (addresses.stream().noneMatch(Address::isDefaultAddress)) addresses.get(0).setDefaultAddress(true);
    }

    private boolean normalize(User user) {
        boolean changed = false;
        if (user.getWishlist() == null) { user.setWishlist(new ArrayList<>()); changed = true; }
        if (user.getCart() == null) { user.setCart(new ArrayList<>()); changed = true; }
        if (user.getAddresses() == null) { user.setAddresses(new ArrayList<>()); changed = true; }

        boolean hasDefault = false;
        int addressNumber = 1;
        for (Address address : user.getAddresses()) {
            if (address.getId() == null || address.getId().isBlank()) { address.setId(UUID.randomUUID().toString()); changed = true; }
            if (address.getLabel() == null || address.getLabel().isBlank()) { address.setLabel(addressNumber == 1 ? "Home" : "Address " + addressNumber); changed = true; }
            if (address.isDefaultAddress()) {
                if (hasDefault) { address.setDefaultAddress(false); changed = true; }
                else hasDefault = true;
            }
            addressNumber++;
        }
        if (!user.getAddresses().isEmpty() && !hasDefault) { user.getAddresses().get(0).setDefaultAddress(true); changed = true; }
        return changed;
    }

    private AccountResponse toResponse(User user) {
        normalize(user);

        List<CartItem> cart = new ArrayList<>(user.getCart());
        List<String> wishlist = new ArrayList<>(user.getWishlist());
        List<Address> addresses = new ArrayList<>(user.getAddresses());

        return new AccountResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getMobile(),
                wishlist,
                cart,
                addresses
        );
    }
}
