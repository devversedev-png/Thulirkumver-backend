package com.thulirkumver.bookstore.security;

import com.thulirkumver.bookstore.service.JwtService;
import com.thulirkumver.bookstore.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserService userService;

    public JwtFilter(JwtService jwtService, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        System.out.println("JWT FILTER: " + request.getMethod() + " " + request.getRequestURI());

        if (header == null || !header.startsWith("Bearer ")) {
            System.out.println("JWT FILTER: No Bearer token");
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = header.substring(7);

            String email = jwtService.getEmail(token);

            System.out.println("JWT FILTER: Email = " + email);

            var user = userService.getByEmail(email);

            String role = user.getRole();

            if (role == null || role.isBlank()) {
                role = "CUSTOMER";
            }

            role = role.toUpperCase().trim();

            // Prevent ROLE_ROLE_ADMIN
            if (role.startsWith("ROLE_")) {
                role = role.substring(5);
            }

            var authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + role)
            );

            System.out.println("JWT FILTER: Role = " + role);
            System.out.println("JWT FILTER: Authority = ROLE_" + role);

            var authentication =
                    new UsernamePasswordAuthenticationToken(
                            email,
                            null,
                            authorities
                    );

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);

            System.out.println("JWT FILTER: Authentication SUCCESS");

        } catch (Exception e) {

            System.out.println(
                    "JWT FILTER ERROR: "
                            + e.getClass().getSimpleName()
                            + " - "
                            + e.getMessage()
            );

            // Do not authenticate this request.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}