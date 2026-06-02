package com.carbonedge.backend.dto;

import java.time.Instant;

public record CourseLaunchResponse(
        Long courseId,
        String launchUrl,
        Instant expiresAt
) {
}
