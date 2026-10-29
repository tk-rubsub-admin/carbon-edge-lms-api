package com.carbonedge.backend.dto;

import java.time.Instant;

public record RegistrationResponse(
        Long userId,
        String email,
        Instant verificationExpiresAt,
        boolean verificationEmailSent,
        boolean firstLogin,
        boolean completedProfile
) {
}
