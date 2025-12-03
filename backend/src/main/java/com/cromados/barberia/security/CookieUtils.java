package com.cromados.barberia.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CookieUtils {
    public static final String ACCESS = "ACCESS";
    public static final String REFRESH = "REFRESH";
    public static final String CSRF = "CSRF";

    public static void add(HttpServletResponse res, String name, String value, int maxAgeSeconds, boolean httpOnly, boolean secure) {
        String sameSite = secure ? "None" : "Lax";

        // Construir cookie manualmente
        StringBuilder cookieStr = new StringBuilder();
        cookieStr.append(name).append("=").append(value);
        cookieStr.append("; Path=/");
        cookieStr.append("; Max-Age=").append(maxAgeSeconds);
        if (httpOnly) cookieStr.append("; HttpOnly");
        if (secure) cookieStr.append("; Secure");
        cookieStr.append("; SameSite=").append(sameSite);

        log.info("[CookieUtils] Setting {} cookie: secure={} httpOnly={} sameSite={}",
                name, secure, httpOnly, sameSite);
        log.debug("[CookieUtils] Cookie value length: {}", value.length());

        res.addHeader("Set-Cookie", cookieStr.toString());

        // Fallback con Cookie API
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(secure);
        res.addCookie(cookie);
    }

    public static void clear(HttpServletResponse res, String name, boolean secure) {
        String[] configs = {
                name + "=; Path=/; Max-Age=0; HttpOnly",
                name + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax",
                name + "=; Path=/; Max-Age=0; HttpOnly; SameSite=None; Secure"
        };

        for (String config : configs) {
            res.addHeader("Set-Cookie", config);
        }

        Cookie cookie = new Cookie(name, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        res.addCookie(cookie);
    }
}