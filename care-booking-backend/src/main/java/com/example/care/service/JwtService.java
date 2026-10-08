package com.example.care.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessMinutes;
    private final long refreshDays;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-minutes}") long accessMinutes,
            @Value("${app.jwt.refresh-days}") long refreshDays
    ) {
        this.key = Keys.hmacShaKeyFor(
            Decoders.BASE64.decode(secret)
        );

        this.accessMinutes = accessMinutes;
        this.refreshDays = refreshDays;
    }

    public String accessToken(Long userId) {
        return create(
            userId,
            "access",
            Instant.now().plus(accessMinutes, ChronoUnit.MINUTES)
        );
    }

    public String refreshToken(Long userId) {
        return create(
            userId,
            "refresh",
            Instant.now().plus(refreshDays, ChronoUnit.DAYS)
        );
    }

    private String create(
            Long userId,
            String type,
            Instant expiresAt
    ) {
        Instant now = Instant.now();

        return Jwts.builder()
            .issuer("care-booking")
            .subject(userId.toString())
            .id(UUID.randomUUID().toString())
            .claim("type", type)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    public Claims parse(String token, String expectedType) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .requireIssuer("care-booking")
            .build()
            .parseSignedClaims(token)
            .getPayload();

        if (!expectedType.equals(
                claims.get("type", String.class))) {
            throw new JwtException("Token 類型錯誤");
        }

        return claims;
    }

    public String hash(String token) {
        try {
            byte[] bytes = MessageDigest
                .getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException(
                "無法計算 Token 雜湊", e
            );
        }
    }
}