package com.thulirkumver.bookstore.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Prevent WebViews and intermediary HTTP caches from serving outdated
 * storefront/catalogue responses after admins publish changes.
 * Does not clear or modify database contents.
 */
@Component
public class ApiFreshnessFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store, max-age=0, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        chain.doFilter(request, response);
    }
}
