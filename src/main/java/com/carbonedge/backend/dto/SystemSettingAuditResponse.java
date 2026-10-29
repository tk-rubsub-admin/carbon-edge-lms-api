package com.carbonedge.backend.dto;

import java.time.Instant;

public record SystemSettingAuditResponse(
        Long id,
        String key,
        String oldValue,
        String newValue,
        boolean secret,
        Instant changedAt,
        String changedBy
) {
}
