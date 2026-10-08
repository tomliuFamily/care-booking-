package com.example.care.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.*;

public final class WorkDtos {

    private WorkDtos() {
    }

    public record CaseRequest(
        @Positive Long customerId,
        @NotBlank @Size(max = 50) String name,
        @NotNull @PastOrPresent LocalDate birthDate,
        @NotBlank @Size(max = 50) String contactName,
        @NotBlank @Size(max = 30) String contactPhone,
        @NotBlank @Size(max = 200) String address,
        @NotBlank @Size(max = 1000) String careNeeds,
        @NotNull Boolean active
    ) {
    }

    public record PolicyRequest(
        @NotNull
        @DecimalMin("0")
        @DecimalMax("100")
        @Digits(integer = 3, fraction = 2)
        BigDecimal subsidyRate,

        @NotNull
        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        BigDecimal subsidyLimit
    ) {
    }

    public record CaseView(
        Long id,
        Long customerId,
        String customerName,
        String name,
        LocalDate birthDate,
        String contactName,
        String contactPhone,
        String address,
        String careNeeds,
        boolean active,
        BigDecimal subsidyRate,
        BigDecimal subsidyLimit
    ) {
    }

    public record NewBookingRequest(
        @NotNull @Positive Long caseId,
        @NotNull @Positive Long serviceId,
        @NotNull @Positive Long slotId,
        @Size(max = 200) String address,
        @Size(max = 500) String note
    ) {
    }

    public record LinkRequest(
        @NotNull @Positive Long caseId
    ) {
    }

    public record RecordRequest(
        @NotNull LocalDateTime actualStart,
        @NotNull LocalDateTime actualEnd,
        @NotBlank @Size(max = 2000) String serviceNote
    ) {
    }

    public record ReviewRequest(
        @NotNull Boolean approve,

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        BigDecimal amount,

        @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record TextRequest(
        @NotBlank @Size(max = 200) String text
    ) {
    }

    public record FinanceView(
        BigDecimal subsidyRate,
        BigDecimal subsidyLimit,
        String claimStatus,
        BigDecimal requestedAmount,
        BigDecimal approvedAmount,
        BigDecimal customerAmount,
        String reviewNote,
        LocalDateTime subsidyReceivedAt,
        String subsidyReference,
        String paymentStatus,
        String paymentReference,
        LocalDateTime paidAt,
        String refundReference,
        LocalDateTime refundedAt
    ) {
    }

    public record WorkView(
        Long bookingId,
        Long caseId,
        String caseName,
        String customerName,
        String caregiverName,
        String serviceName,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String bookingStatus,
        BigDecimal price,
        LocalDateTime actualStart,
        LocalDateTime actualEnd,
        String serviceNote,
        boolean recordEditable,
        FinanceView finance
    ) {
    }

    public record AuditView(
        Long id,
        String actorName,
        String action,
        String detail,
        LocalDateTime createdAt
    ) {
    }
}