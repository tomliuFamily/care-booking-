package com.example.care.repository;

import java.util.List;
import java.util.Optional;

import com.example.care.entity.Booking;
import com.example.care.entity.CareCase;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CareCaseRepository
        extends JpaRepository<CareCase, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CareCase c where c.id = :id")
    Optional<CareCase> lockById(@Param("id") Long id);

    @Query("""
        select c
        from CareCase c
        where :role = 'ADMIN'
           or (
                :role = 'CUSTOMER'
                and c.customer.id = :userId
           )
           or (
                :role = 'CAREGIVER'
                and exists (
                    select w.id
                    from CareWorkflow w
                    where w.careCase = c
                      and w.booking.slot.caregiver.id = :userId
                      and w.booking.status <> :cancelled
                )
           )
        order by c.id desc
        """)
    List<CareCase> findVisible(
        @Param("userId") Long userId,
        @Param("role") String role,
        @Param("cancelled") Booking.Status cancelled
    );
}