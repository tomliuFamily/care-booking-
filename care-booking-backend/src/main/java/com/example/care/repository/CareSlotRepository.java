package com.example.care.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.example.care.entity.CareSlot;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CareSlotRepository
        extends JpaRepository<CareSlot, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CareSlot s where s.id = :id")
    Optional<CareSlot> lockById(@Param("id") Long id);

    @Query("""
        select s
        from CareSlot s
        join fetch s.caregiver
        where s.startAt >= :from
          and s.startAt < :to
        order by s.startAt, s.caregiver.id
        """)
    List<CareSlot> findInMonth(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );

    @Query("""
        select count(s)
        from CareSlot s
        where s.caregiver.id = :caregiverId
          and s.startAt < :endAt
          and s.endAt > :startAt
        """)
    long countOverlapping(
        @Param("caregiverId") Long caregiverId,
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt
    );
}