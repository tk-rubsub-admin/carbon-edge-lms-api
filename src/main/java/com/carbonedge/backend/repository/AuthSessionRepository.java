package com.carbonedge.backend.repository;

import com.carbonedge.backend.model.AuthSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    Optional<AuthSession> findByAccessToken(String accessToken);
}
