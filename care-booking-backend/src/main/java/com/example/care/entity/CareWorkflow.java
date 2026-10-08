package com.example.care.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "care_workflow")
@Getter
@Setter
@NoArgsConstructor
public class CareWorkflow {

    public enum ClaimStatus {
        DRAFT,
        SUBMITTED,
        REJECTED,
        APPROVED
    }

    public enum PaymentStatus {
        UNPAID,
        REPORTED,
        PAID,
        WAIVED,
        REFUND_REQUESTED,
        REFUNDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "booking_id",
        nullable = false,
        unique = true
    )
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private CareCase careCase;

    // 保存綁定當時的補助設定。
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal subsidyRate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subsidyLimit;

    private LocalDateTime actualStart;

    private LocalDateTime actualEnd;

    @Column(nullable = false, length = 2000)
    private String serviceNote = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus claimStatus = ClaimStatus.DRAFT;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal requestedAmount = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal approvedAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal customerAmount;

    @Column(nullable = false, length = 500)
    private String reviewNote = "";

    private LocalDateTime subsidyReceivedAt;

    @Column(nullable = false, length = 200)
    private String subsidyReference = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(nullable = false, length = 200)
    private String paymentReference = "";

    private LocalDateTime paidAt;

    @Column(nullable = false, length = 200)
    private String refundReference = "";

    private LocalDateTime refundedAt;
}