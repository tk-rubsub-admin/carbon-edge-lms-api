package com.carbonedge.backend.repository;

import com.carbonedge.backend.model.AuthSession;
import java.util.Optional;
import java.time.Instant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    Optional<AuthSession> findByAccessToken(String accessToken);

    @Query("select session from AuthSession session join fetch session.user where session.accessToken = :accessToken")
    Optional<AuthSession> findWithUserByAccessToken(@Param("accessToken") String accessToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthSession> findForRefreshByRefreshTokenHash(String refreshTokenHash);

    @Modifying
    @Query("update AuthSession session set session.revokedAt = :revokedAt where session.user.id = :userId and session.revokedAt is null")
    void revokeActiveSessionsByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);
}
