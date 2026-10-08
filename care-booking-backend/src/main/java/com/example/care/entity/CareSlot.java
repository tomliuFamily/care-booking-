package com.example.care.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "care_slot",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_caregiver_slot_start",
        columnNames = {"caregiver_id", "start_at"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class CareSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caregiver_id", nullable = false)
    private UserAccount caregiver;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    public CareSlot(
            UserAccount caregiver,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        this.caregiver = caregiver;
        this.startAt = startAt;
        this.endAt = endAt;
    }
}