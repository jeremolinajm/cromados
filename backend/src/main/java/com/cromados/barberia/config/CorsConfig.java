package com.cromados.barberia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${cromados.cors.allowed-origins:}")
    private String allowedOriginsCSV;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();

        // tolerar null/empty y dar defaults de dev
        String csv = (allowedOriginsCSV == null) ? "" : allowedOriginsCSV.trim();
        List<String> origins = csv.isEmpty()
                ? List.of("http://localhost:5173", "http://127.0.0.1:5173", "https://*.trycloudflare.com")
                : Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        cfg.setAllowedOriginPatterns(origins);
        cfg.setAllowedOrigins(java.util.Collections.emptyList());
        cfg.setAllowedMethods(Arrays.asList("GET","POST","PUT","PATCH","DELETE","OPTIONS","HEAD"));
        cfg.setAllowedHeaders(Arrays.asList("*", "Authorization", "Content-Type", "Accept", "X-CSRF-Token", "X-Requested-With"));
        cfg.setAllowCredentials(true);
        cfg.setExposedHeaders(Arrays.asList("Set-Cookie","Location"));
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
