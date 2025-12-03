package com.cromados.barberia.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthService {

    private final String username;
    private final String passwordHash;
    private final PasswordEncoder enc;

    public AdminAuthService(
            @Value("${admin.username}") String username,
            @Value("${admin.passwordHash}") String passwordHash,
            PasswordEncoder enc
    ) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.enc = enc;
    }

    public boolean authenticate(String user, String pass) {
        if (user == null || pass == null) return false;
        if (!username.equals(user)) return false;

        // soporta {noop}... o bcrypt, etc.
        if (passwordHash.startsWith("{noop}")) {
            return passwordHash.substring("{noop}".length()).equals(pass);
        }
        return enc.matches(pass, passwordHash);
    }
}
