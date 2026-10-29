package com.carbonedge.backend.dto;

import java.time.Instant;

public record SystemSettingResponse(
        String key,
        String value,
        boolean secret,
        boolean configured,
        Instant updatedAt,
        String updatedBy
) {
}
