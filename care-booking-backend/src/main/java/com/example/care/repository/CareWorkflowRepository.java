package com.example.care.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.example.care.entity.CareWorkflow;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CareWorkflowRepository
        extends JpaRepository<CareWorkflow, Long> {

    Optional<CareWorkflow> findByBookingId(Long bookingId);

    boolean existsByBookingId(Long bookingId);

    @Query("""
        select w
        from CareWorkflow w
        where w.booking.slot.startAt >= :from
          and w.booking.slot.startAt < :to
          and (
                :role = 'ADMIN'
                or (
                    :role = 'CUSTOMER'
                    and w.booking.customer.id = :userId
                )
                or (
                    :role = 'CAREGIVER'
                    and w.booking.slot.caregiver.id = :userId
                )
          )
        order by w.booking.slot.startAt desc, w.id desc
        """)
    List<CareWorkflow> findVisible(
        @Param("userId") Long userId,
        @Param("role") String role,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );
}