// src/main/java/com/cromados/barberia/controller/AuthController.java
package com.cromados.barberia.controller;

import com.cromados.barberia.security.CookieUtils;
import com.cromados.barberia.security.JwtService;
import com.cromados.barberia.service.AdminAuthService;
import jakarta.servlet.http.Cookie;  // ✅ AGREGAR ESTE IMPORT
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AdminAuthService admin;
    private final JwtService jwt;
    private final boolean secureCookies;

    public AuthController(AdminAuthService admin, JwtService jwt,
                          @Value("${server.ssl.enabled:false}") boolean sslEnabled,
                          @Value("${app.forceSecureCookies:false}") boolean forceSecureCookies) {
        this.admin = admin;
        this.jwt = jwt;
        this.secureCookies = sslEnabled || forceSecureCookies;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginReq req, HttpServletResponse res) {
        if (!admin.authenticate(req.getUsername(), req.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "error", "invalid_credentials"));
        }

        String access = jwt.createAccess(req.getUsername(), List.of("ADMIN"));
        String refresh = jwt.createRefresh(req.getUsername());
        String csrf = randomCsrf();

        // Intentar setear cookies (funcionará en producción con mismo dominio)
        CookieUtils.add(res, CookieUtils.ACCESS, access, 60 * 60, true, secureCookies);
        CookieUtils.add(res, CookieUtils.REFRESH, refresh, 60 * 60 * 24 * 15, true, secureCookies);
        CookieUtils.add(res, CookieUtils.CSRF, csrf, 60 * 60 * 24 * 7, false, secureCookies);

        log.info("[login] Cookies establecidas para usuario: {}", req.getUsername());
        log.info("[login] secureCookies={}", secureCookies);

        // ✅ CRÍTICO: Devolver tokens en JSON para desarrollo con Cloudflare Tunnel
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "access", access,   // ⚠️ Solo para desarrollo
                "refresh", refresh, // ⚠️ Solo para desarrollo
                "csrf", csrf
        ));
    }

    private static String randomCsrf() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest req, HttpServletResponse res,
                                     @RequestHeader(value = "X-CSRF-Token", required = false) String csrfHeader) {
        var cookies = req.getCookies();
        String csrfCookie = null, refresh = null;
        if (cookies != null) {
            for (var c : cookies) {
                if (CookieUtils.CSRF.equals(c.getName())) csrfCookie = c.getValue();
                if (CookieUtils.REFRESH.equals(c.getName())) refresh = c.getValue();
            }
        }
        if (csrfCookie == null || csrfHeader == null || !csrfCookie.equals(csrfHeader)) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "error", "csrf_invalid"));
        }
        if (refresh == null || refresh.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "error", "no_refresh"));
        }

        try {
            var jws = jwt.parse(refresh);
            if (!jwt.isRefresh(jws)) {
                return ResponseEntity.status(401).body(Map.of("ok", false, "error", "not_refresh"));
            }
            String sub = jws.getBody().getSubject();
            String newAccess = jwt.createAccess(sub, List.of("ADMIN"));
            String newRefresh = jwt.createRefresh(sub);

            CookieUtils.add(res, CookieUtils.ACCESS, newAccess, 60 * 60, true, secureCookies);
            CookieUtils.add(res, CookieUtils.REFRESH, newRefresh, 60 * 60 * 24 * 15, true, secureCookies);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "error", "refresh_invalid"));
        }
    }
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            HttpServletRequest req,
            HttpServletResponse res,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrfHeader
    ) {
        // ✅ Leer CSRF de cookie
        String csrfCookie = null;
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (CookieUtils.CSRF.equals(c.getName())) {
                    csrfCookie = c.getValue();
                    break;
                }
            }
        }

        // ✅ DEBUG: Loguear para diagnóstico (quitar en prod)
        log.debug("[logout] CSRF Cookie: {}", csrfCookie != null ? csrfCookie.substring(0, 8) + "..." : "null");
        log.debug("[logout] CSRF Header: {}", csrfHeader != null ? csrfHeader.substring(0, 8) + "..." : "null");

        // ⚠️ IMPORTANTE: Para logout, ser más permisivo
        // Si hay CSRF, validar. Si no hay, permitir igual (sesión expirada/corrupta)
        if (csrfCookie != null && csrfHeader != null && !csrfCookie.equals(csrfHeader)) {
            log.warn("[logout] CSRF mismatch pero permitiendo logout de todas formas");
            // NO retornar 403, seguir adelante
        }

        // ✅ Limpiar contexto
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(null);

        // ✅ Limpiar cookies con múltiples variantes
        clearCookieAllVariants(res, CookieUtils.ACCESS);
        clearCookieAllVariants(res, CookieUtils.REFRESH);
        clearCookieAllVariants(res, CookieUtils.CSRF);

        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ✅ Helper para limpiar con todas las variantes posibles
    private void clearCookieAllVariants(HttpServletResponse res, String name) {
        // Variante 1: SameSite=Lax
        res.addHeader("Set-Cookie",
                name + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");

        // Variante 2: SameSite=None; Secure (para HTTPS)
        if (secureCookies) {
            res.addHeader("Set-Cookie",
                    name + "=; Path=/; Max-Age=0; HttpOnly; SameSite=None; Secure");
        }

        // Variante 3: Sin SameSite (fallback)
        Cookie c = new Cookie(name, "");
        c.setPath("/");
        c.setMaxAge(0);
        c.setHttpOnly(true);
        c.setSecure(secureCookies);
        res.addCookie(c);
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(HttpServletResponse res) {
        String token = UUID.randomUUID().toString();
        ResponseCookie cookie = ResponseCookie.from(CookieUtils.CSRF, token)
                .httpOnly(false)
                .secure(secureCookies)
                .sameSite("None")
                .path("/")
                .maxAge(Duration.ofHours(1))
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(Map.of("csrf", token));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest req) {
        // ✅ Verificar que hay cookie ACCESS
        String accessToken = null;
        if (req.getCookies() != null) {
            for (Cookie c : req.getCookies()) {
                if (CookieUtils.ACCESS.equals(c.getName())) {
                    accessToken = c.getValue();
                    break;
                }
            }
        }

        // ✅ Sin token = no autenticado
        if (accessToken == null || accessToken.isBlank()) {
            log.debug("[/me] Sin token ACCESS");
            return ResponseEntity.status(401).body(Map.of("ok", false));
        }

        // ✅ Verificar contexto de seguridad
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() ||
                "anonymousUser".equals(auth.getPrincipal())) {
            log.debug("[/me] Token presente pero usuario no autenticado");
            return ResponseEntity.status(401).body(Map.of("ok", false));
        }

        String username = String.valueOf(auth.getPrincipal());
        log.info("[/me] Usuario autenticado: {}", username);

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "user", username,
                "roles", auth.getAuthorities()
        ));
    }

    @Data
    public static class LoginReq {
        @NotBlank private String username;
        @NotBlank private String password;
    }
}