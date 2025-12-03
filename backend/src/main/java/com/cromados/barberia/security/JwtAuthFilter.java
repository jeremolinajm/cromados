package com.cromados.barberia.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        try {
            String token = null;

            // Primero cookie ACCESS
            if (req.getCookies() != null) {
                for (Cookie c : req.getCookies()) {
                    if (CookieUtils.ACCESS.equals(c.getName())) {
                        token = c.getValue();
                        break;
                    }
                }
            }
            // fallback: Authorization: Bearer (dev tools)
            if (token == null) {
                String auth = req.getHeader(HttpHeaders.AUTHORIZATION);
                if (auth != null && auth.startsWith("Bearer ")) {
                    token = auth.substring(7);
                }
            }

            if (token != null && !token.isBlank()) {
                final String tokenValue = token;   // << asegurar “efectivamente final”
                Jws<Claims> jws = jwt.parse(tokenValue);
                String sub = jws.getBody().getSubject();
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) jws.getBody().get("roles");
                List<SimpleGrantedAuthority> auths = new ArrayList<>();
                if (roles != null) {
                    for (String r : roles) auths.add(new SimpleGrantedAuthority("ROLE_" + r));
                }

                AbstractAuthenticationToken at = new AbstractAuthenticationToken(auths) {
                    @Override public Object getCredentials() { return tokenValue; }
                    @Override public Object getPrincipal() { return sub; }
                };
                at.setAuthenticated(true);
                SecurityContextHolder.getContext().setAuthentication(at);
            }
        } catch (Exception ex) {
            // ignoramos token inválido => queda anónimo
        }

        chain.doFilter(req, res);
    }
}
