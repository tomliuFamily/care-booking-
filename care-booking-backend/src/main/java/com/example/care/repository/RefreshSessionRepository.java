package com.example.care.repository;

import java.util.Optional;

import com.example.care.entity.RefreshSession;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RefreshSessionRepository
        extends JpaRepository<RefreshSession, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r
        from RefreshSession r
        where r.tokenHash = :hash
        """)
    Optional<RefreshSession> lockByHash(
        @Param("hash") String hash
    );
}