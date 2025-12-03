package com.cromados.barberia.security;

import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

public class CsrfFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECT_METHODS = Set.of("POST","PUT","PATCH","DELETE");

    // ✅ TEMPORAL: Cambiar a false cuando subas a producción
    private static final boolean DISABLE_FOR_DEV = false;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        // ✅ BYPASS TEMPORAL PARA DESARROLLO
        if (DISABLE_FOR_DEV) {
            chain.doFilter(req, res);
            return;
        }

        String path = req.getRequestURI();
        String method = req.getMethod();

        // Resto del código sin cambios...
        if (path.equals("/auth/logout")) {
            chain.doFilter(req, res);
            return;
        }

        if (path.startsWith("/api/") ||
                path.startsWith("/auth/csrf") ||
                path.startsWith("/auth/login") ||
                path.startsWith("/pagos/checkout") ||
                path.startsWith("/pagos/webhook") ||
                path.startsWith("/uploads/") ||
                path.startsWith("/barberos/")) {
            chain.doFilter(req, res);
            return;
        }

        if (!PROTECT_METHODS.contains(method)) {
            chain.doFilter(req, res);
            return;
        }

        String header = req.getHeader("X-CSRF-Token");
        String cookie = null;
        if (req.getCookies() != null) {
            for (Cookie c : req.getCookies()) {
                if (CookieUtils.CSRF.equals(c.getName())) {
                    cookie = c.getValue();
                    break;
                }
            }
        }

        if (cookie == null || header == null || header.isBlank() || !header.equals(cookie)) {
            res.setStatus(403);
            res.getWriter().write("CSRF token invalid");
            return;
        }

        chain.doFilter(req, res);
    }
}