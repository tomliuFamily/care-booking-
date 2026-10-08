package com.example.care.repository;

import java.util.List;

import com.example.care.entity.CareService;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareServiceRepository
        extends JpaRepository<CareService, Long> {

    List<CareService> findByActiveTrueOrderById();
}