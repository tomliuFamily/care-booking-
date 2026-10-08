package com.example.care.controller;

import java.util.Map;

import com.example.care.dto.Dtos;
import com.example.care.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public Dtos.TokenResponse register(
            @Valid @RequestBody
            Dtos.RegisterRequest request
    ) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public Dtos.TokenResponse login(
            @Valid @RequestBody
            Dtos.LoginRequest request
    ) {
        return auth.login(request);
    }

    @PostMapping("/refresh")
    public Dtos.TokenResponse refresh(
            @Valid @RequestBody
            Dtos.RefreshRequest request
    ) {
        return auth.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(
            @Valid @RequestBody
            Dtos.RefreshRequest request
    ) {
        auth.logout(request.refreshToken());

        return Map.of("message", "已登出");
    }
}