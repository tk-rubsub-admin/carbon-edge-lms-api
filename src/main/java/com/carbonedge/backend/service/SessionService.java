package com.carbonedge.backend.service;

import com.carbonedge.backend.exception.UnauthorizedException;
import com.carbonedge.backend.model.AuthSession;
import com.carbonedge.backend.model.UserAccount;
import com.carbonedge.backend.repository.AuthSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthSessionRepository authSessionRepository;

    @Value("${app.session.access-token-ttl:PT15M}")
    private Duration accessTokenTtl;

    @Value("${app.session.refresh-token-ttl:P30D}")
    private Duration refreshTokenTtl;

    @Transactional
    public IssuedSession create(UserAccount user) {
        String refreshToken = newToken();
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setAccessToken(newToken());
        session.setExpiresAt(Instant.now().plus(accessTokenTtl));
        session.setRefreshTokenHash(hash(refreshToken));
        session.setRefreshExpiresAt(Instant.now().plus(refreshTokenTtl));
        return new IssuedSession(authSessionRepository.save(session), refreshToken);
    }

    /** Rotates both tokens. A refresh token can be used exactly once. */
    @Transactional
    public IssuedSession refresh(String rawRefreshToken) {
        AuthSession session = authSessionRepository.findForRefreshByRefreshTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (!session.isRefreshActive()) {
            throw new UnauthorizedException("Refresh token has expired or session was revoked");
        }

        String nextRefreshToken = newToken();
        session.setAccessToken(newToken());
        session.setExpiresAt(Instant.now().plus(accessTokenTtl));
        session.setRefreshTokenHash(hash(nextRefreshToken));
        session.setRefreshExpiresAt(Instant.now().plus(refreshTokenTtl));
        session.setLastRefreshedAt(Instant.now());
        return new IssuedSession(authSessionRepository.save(session), nextRefreshToken);
    }

    @Transactional
    public void revokeByAccessToken(String accessToken) {
        authSessionRepository.findByAccessToken(accessToken).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(Instant.now());
                authSessionRepository.save(session);
            }
        });
    }

    @Transactional
    public void revokeAllForUser(UserAccount user) {
        authSessionRepository.revokeActiveSessionsByUserId(user.getId(), Instant.now());
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash session token", exception);
        }
    }
}
