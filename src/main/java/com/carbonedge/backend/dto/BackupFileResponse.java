package com.carbonedge.backend.dto;

import java.time.Instant;

public record BackupFileResponse(String id, String database, long sizeBytes, Instant createdAt) {
}
