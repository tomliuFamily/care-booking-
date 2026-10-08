package com.example.care.service;

import java.time.Instant;
import java.util.Locale;

import com.example.care.dto.Dtos;
import com.example.care.entity.RefreshSession;
import com.example.care.entity.UserAccount;
import com.example.care.entity.UserAccount.Role;
import com.example.care.repository.RefreshSessionRepository;
import com.example.care.repository.UserRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(
            UserRepository users,
            RefreshSessionRepository sessions,
            PasswordEncoder encoder,
            JwtService jwt
    ) {
        this.users = users;
        this.sessions = sessions;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public Dtos.TokenResponse register(
            Dtos.RegisterRequest request
    ) {
        String email = normalizeEmail(request.email());

        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "此 Email 已註冊"
            );
        }

        checkPasswordBytes(request.password());

        // 公開註冊一律是 CUSTOMER
        UserAccount user = users.save(
            new UserAccount(
                email,
                encoder.encode(request.password()),
                request.name().trim(),
                Role.CUSTOMER
            )
        );

        return issue(user);
    }

    @Transactional
    public Dtos.TokenResponse login(
            Dtos.LoginRequest request
    ) {
        UserAccount user = users.findByEmail(
            normalizeEmail(request.email())
        ).orElseThrow(this::unauthorized);

        checkPasswordBytes(request.password());

        if (!encoder.matches(
                request.password(),
                user.getPassword())) {
            throw unauthorized();
        }

        return issue(user);
    }

    @Transactional
    public Dtos.TokenResponse refresh(String rawToken) {
        Claims claims = parseRefresh(rawToken);

        RefreshSession session = sessions.lockByHash(
            jwt.hash(rawToken)
        ).orElseThrow(this::unauthorized);

        if (session.isRevoked()
                || !session.getExpiresAt().isAfter(Instant.now())
                || !session.getUser().getId().toString()
                    .equals(claims.getSubject())) {
            throw unauthorized();
        }

        // 每次更新後撤銷舊 Refresh Token
        session.setRevoked(true);

        return issue(session.getUser());
    }

    @Transactional
    public void logout(String rawToken) {
        // 即使 Token 已過期，也可以將對應紀錄撤銷
        sessions.lockByHash(jwt.hash(rawToken))
            .ifPresent(session -> session.setRevoked(true));
    }

    private Dtos.TokenResponse issue(UserAccount user) {
        String access = jwt.accessToken(user.getId());
        String refresh = jwt.refreshToken(user.getId());

        Instant expiresAt = jwt.parse(refresh, "refresh")
            .getExpiration()
            .toInstant();

        sessions.save(
            new RefreshSession(
                jwt.hash(refresh),
                user,
                expiresAt
            )
        );

        return new Dtos.TokenResponse(
            access,
            refresh,
            new Dtos.UserView(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().name()
            )
        );
    }

    private Claims parseRefresh(String rawToken) {
        try {
            return jwt.parse(rawToken, "refresh");
        } catch (JwtException | IllegalArgumentException e) {
            throw unauthorized();
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void checkPasswordBytes(String password) {
        if (password.getBytes(
                java.nio.charset.StandardCharsets.UTF_8
            ).length > 72) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "密碼 UTF-8 長度不可超過 72 bytes"
            );
        }
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "帳號密碼錯誤，或登入已失效"
        );
    }
}