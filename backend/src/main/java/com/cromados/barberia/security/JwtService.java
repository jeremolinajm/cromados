package com.cromados.barberia.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class JwtService {

    private final String issuer;
    private final String audience;
    private final int accessExpMinutes;
    private final int refreshExpDays;

    private final PublicKey publicKey;
    private final PrivateKey privateKey;

    public JwtService(
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.audience}") String audience,
            @Value("${security.jwt.access.expMinutes}") int accessExpMinutes,
            @Value("${security.jwt.refresh.expDays}") int refreshExpDays,
            @Value("${security.jwt.publicKeyPem:}") String publicPem,
            @Value("${security.jwt.privateKeyPem:}") String privatePem,
            @Value("${security.jwt.publicKeyPath:}") String publicPath,
            @Value("${security.jwt.privateKeyPath:}") String privatePath
    ) {
        this.issuer = issuer;
        this.audience = audience;
        this.accessExpMinutes = accessExpMinutes;
        this.refreshExpDays = refreshExpDays;

        try {
            if (!publicPem.isBlank() && !privatePem.isBlank()) {
                this.publicKey = readPublicFromPem(publicPem);
                this.privateKey = readPrivateFromPem(privatePem);
            } else if (!publicPath.isBlank() && !privatePath.isBlank()) {
                this.publicKey = readPublicFromPem(Files.readString(Path.of(publicPath)));
                this.privateKey = readPrivateFromPem(Files.readString(Path.of(privatePath)));
            } else {
                throw new IllegalStateException("JWT keys not provided (use PEM in env or file paths).");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error loading JWT keys: " + e.getMessage(), e);
        }
    }

    private static PublicKey readPublicFromPem(String pem) throws Exception {
        String clean = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] bytes = Decoders.BASE64.decode(clean);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(bytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private static PrivateKey readPrivateFromPem(String pem) throws Exception {
        String clean = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] bytes = Decoders.BASE64.decode(clean);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    public String createAccess(String subject, Collection<String> roles) {
        Instant now = Instant.now();
        Instant exp = now.plus(accessExpMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .setIssuer(issuer)
                .setAudience(audience)
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setNotBefore(Date.from(now.minusSeconds(5)))
                .setExpiration(Date.from(exp))
                .claim("roles", roles)
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    public String createRefresh(String subject) {
        Instant now = Instant.now();
        Instant exp = now.plus(refreshExpDays, ChronoUnit.DAYS);
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .setIssuer(issuer)
                .setAudience(audience)
                .setSubject(subject)
                .setId(jti)
                .setIssuedAt(Date.from(now))
                .setNotBefore(Date.from(now.minusSeconds(5)))
                .setExpiration(Date.from(exp))
                .claim("typ", "refresh")
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .requireIssuer(issuer)
                .requireAudience(audience)
                .verifyWith(publicKey)       // << reemplaza setSigningKey() en 0.12.x
                .build()
                .parseSignedClaims(token);   // << reemplaza parseClaimsJws()
    }

    public boolean isRefresh(Jws<Claims> jws) {
        Object typ = jws.getBody().get("typ");
        return "refresh".equals(typ);
    }
}
