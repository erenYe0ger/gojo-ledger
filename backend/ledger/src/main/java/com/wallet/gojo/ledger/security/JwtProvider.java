package com.wallet.gojo.ledger.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtProvider {

    @Value("${app.jwt.secret:change-me-in-production-to-a-secure-secret-key-min-256-bits}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-minutes:10}")
    private long jwtExpirationMins;

    @Value("${app.jwt.issuer:gojo-ledger}")
    private String jwtIssuer;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }


    public String generateToken(SecurityUser userDetails) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtExpirationMins * 60);

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuer(jwtIssuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("userId", userDetails.getId().toString())
                .signWith(secretKey)
                .compact();
    }


    public String getUsernameFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }


    public UUID getUserIdFromToken(String token) {
        String userId = getClaimsFromToken(token).get("userId", String.class);
        return UUID.fromString(userId);
    }


    public boolean validateToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token: {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    private Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
