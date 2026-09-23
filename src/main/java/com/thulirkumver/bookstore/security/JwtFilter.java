package com.thulirkumver.bookstore.security;

import com.thulirkumver.bookstore.service.JwtService;
import com.thulirkumver.bookstore.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);
    public static final String JWT_ERROR_ATTRIBUTE = "tvb.jwt.error";

    private final JwtService jwtService;
    private final UserService userService;

    public JwtFilter(
            JwtService jwtService,
            UserService userService
    ) {
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

        if (header != null
                && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = header.substring(7).trim();

            if (token.isBlank()) {
                request.setAttribute(JWT_ERROR_ATTRIBUTE, "Missing bearer token");
            } else {
                try {
                    String email = jwtService.getEmail(token);
                    var user = userService.getByEmail(email);

                    String role = user.getRole();
                    if (role == null || role.isBlank()) {
                        role = "CUSTOMER";
                    }

                    role = role.trim().toUpperCase();
                    if (role.startsWith("ROLE_")) {
                        role = role.substring(5);
                    }

                    var authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + role)
                    );

                    var authentication = new UsernamePasswordAuthenticationToken(
                            email,
                            null,
                            authorities
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (Exception ex) {
                    // Do not silently swallow an invalid token. Leaving the
                    // SecurityContext unauthenticated lets Spring Security's
                    // AuthenticationEntryPoint return a proper 401 JSON response.
                    request.setAttribute(JWT_ERROR_ATTRIBUTE, ex.getClass().getSimpleName());
                    log.warn("JWT authentication failed for {}: {}", request.getRequestURI(), ex.getMessage());
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
