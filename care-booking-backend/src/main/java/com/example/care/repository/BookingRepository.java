package com.example.care.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.example.care.entity.Booking;
import com.example.care.entity.Booking.Status;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BookingRepository
        extends JpaRepository<Booking, Long> {

    boolean existsBySlotIdAndStatusNot(
        Long slotId,
        Status status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> lockById(@Param("id") Long id);

    @Query("""
        select count(b)
        from Booking b
        where b.customer.id = :customerId
          and b.status <> :cancelled
          and b.slot.startAt < :endAt
          and b.slot.endAt > :startAt
        """)
    long countCustomerOverlap(
        @Param("customerId") Long customerId,
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt,
        @Param("cancelled") Status cancelled
    );

    @Query("""
        select b.slot.id
        from Booking b
        where b.status <> :cancelled
          and b.slot.startAt >= :from
          and b.slot.startAt < :to
        """)
    List<Long> occupiedSlotIds(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        @Param("cancelled") Status cancelled
    );
}