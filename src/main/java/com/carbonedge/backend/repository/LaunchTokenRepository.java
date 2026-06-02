package com.carbonedge.backend.repository;

import com.carbonedge.backend.model.LaunchToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LaunchTokenRepository extends JpaRepository<LaunchToken, Long> {

    Optional<LaunchToken> findByJti(String jti);

    @Modifying
    @Query("""
            update LaunchToken t
            set t.consumedAt = :consumedAt
            where t.jti = :jti
              and t.consumedAt is null
              and t.expiresAt > :now
            """)
    int consumeIfPending(
            @Param("jti") String jti,
            @Param("consumedAt") Instant consumedAt,
            @Param("now") Instant now
    );
}
