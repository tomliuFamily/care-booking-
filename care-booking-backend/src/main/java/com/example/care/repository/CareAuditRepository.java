package com.example.care.repository;

import java.util.List;

import com.example.care.entity.CareAudit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CareAuditRepository
        extends JpaRepository<CareAudit, Long> {

    List<CareAudit> findByBookingIdOrderByIdAsc(Long bookingId);
}