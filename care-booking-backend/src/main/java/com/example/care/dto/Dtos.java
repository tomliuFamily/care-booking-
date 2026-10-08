package com.example.care.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.*;

public final class Dtos {

    private Dtos() {
    }

    public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(max = 60) String password
    ) {
    }

    public record RegisterRequest(
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 8, max = 60) String password,
        @NotBlank @Size(max = 50) String name
    ) {
    }

    public record RefreshRequest(
        @NotBlank @Size(max = 2048) String refreshToken
    ) {
    }

    public record UserView(
        Long id,
        String email,
        String name,
        String role
    ) {
    }

    public record TokenResponse(
        String accessToken,
        String refreshToken,
        UserView user
    ) {
    }

    public record ServiceView(
        Long id,
        String name,
        String description,
        BigDecimal price
    ) {
    }

    public record CaregiverView(
        Long id,
        String name
    ) {
    }

    public record SlotView(
        Long id,
        Long caregiverId,
        String caregiverName,
        LocalDateTime startAt,
        LocalDateTime endAt
    ) {
    }

    public record CreateSlotRequest(
        @NotNull @Positive Long caregiverId,
        @NotNull LocalDateTime startAt
    ) {
    }

    public record CreateBookingRequest(
        @NotNull @Positive Long serviceId,
        @NotNull @Positive Long slotId,
        @NotBlank @Size(max = 200) String address,
        @Size(max = 500) String note
    ) {
    }

    public record ChangeStatusRequest(
        @NotBlank String status
    ) {
    }

    // MyBatis 填入欄位，Jackson 回傳 JSON
    public static class BookingView {

        public Long id;
        public String customerName;
        public String caregiverName;
        public String serviceName;
        public LocalDateTime startAt;
        public LocalDateTime endAt;
        public String status;
        public BigDecimal price;
        public String address;
        public String note;
    }
}