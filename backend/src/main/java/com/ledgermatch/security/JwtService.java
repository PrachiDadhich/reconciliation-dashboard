package com.ledgermatch.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    public JwtService(@Value("${app.jwt-secret}") String secret) {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    public String issue(UUID userId, String email) {
        return Jwts.builder().subject(userId.toString()).claim("email", email)
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 86_400_000L))
                .signWith(key).compact();
    }
    public UUID userId(String token) { return UUID.fromString(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject()); }
}