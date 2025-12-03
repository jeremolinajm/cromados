package com.cromados.barberia.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;


@Configuration
public class SecurityConfig {

    private final JwtService jwt;

    public SecurityConfig(JwtService jwt) {
        this.jwt = jwt;
    }

    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable()) // ya lo tienes deshabilitado globalmente
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers("/auth/login", "/auth/csrf", "/auth/logout").permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/pagos/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, "/pagos/webhook").permitAll()
                        .requestMatchers(HttpMethod.POST, "/pagos/checkout").permitAll() // ✅ AGREGAR ESTO
                        .requestMatchers("/public/**").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/api/**").permitAll() // ✅ asegúrate que esté
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll()
                )
                .addFilterBefore(new CsrfFilter(), BasicAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthFilter(jwt), CsrfFilter.class)
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
