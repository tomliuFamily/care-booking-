package com.example.care.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "care_case")
@Getter
@Setter
@NoArgsConstructor
public class CareCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private UserAccount customer;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false, length = 50)
    private String contactName;

    @Column(nullable = false, length = 30)
    private String contactPhone;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false, length = 1000)
    private String careNeeds;

    @Column(nullable = false)
    private boolean active = true;

    // 示範補助比例，0～100。
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal subsidyRate = BigDecimal.ZERO;

    // 示範規則：每筆預約的補助上限。
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subsidyLimit = BigDecimal.ZERO;
}