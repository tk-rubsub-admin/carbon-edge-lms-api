package com.carbonedge.backend.dto;

import java.time.Instant;

public record BackupJobResponse(String id, String status, String message, Instant startedAt, Instant finishedAt) {
}
