package com.example.care.config;

import java.io.IOException;
import java.util.List;

import com.example.care.repository.UserRepository;
import com.example.care.service.JwtService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication
    .UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority
    .SimpleGrantedAuthority;
import org.springframework.security.core.context
    .SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final UserRepository users;

    public JwtFilter(
            JwtService jwt,
            UserRepository users
    ) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {
        return request.getServletPath()
            .startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            try {
                String token = header.substring(7);
                Claims claims = jwt.parse(token, "access");
                Long userId = Long.valueOf(claims.getSubject());

                var user = users.findById(userId)
                    .orElseThrow(
                        () -> new JwtException("帳號不存在")
                    );

                var authority = new SimpleGrantedAuthority(
                    "ROLE_" + user.getRole().name()
                );

                var authentication =
                    new UsernamePasswordAuthenticationToken(
                        user.getId(),
                        null,
                        List.of(authority)
                    );

                SecurityContextHolder.getContext()
                    .setAuthentication(authentication);

            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();

                response.setStatus(401);
                response.setContentType(
                    "application/json;charset=UTF-8"
                );

                response.getWriter().write(
                    "{\"message\":\"Access Token 無效或已過期\"}"
                );

                return;
            }
        }

        chain.doFilter(request, response);
    }
}