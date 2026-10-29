package com.carbonedge.backend.dto;

import java.time.Instant;

public record EmailDispatchResponse(
        boolean sent,
        Instant expiresAt
) {
}
