package com.carbonedge.backend.dto;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        Instant expiresAt,
        String refreshToken,
        Instant refreshExpiresAt,
        UserProfileResponse user
) {
}
